package com.ep.databuilder.factory;

import com.ep.databuilder.common.BizException;
import com.ep.databuilder.common.PageResult;
import com.ep.databuilder.common.SqlGuard;
import com.ep.databuilder.factory.FactoryDTOs.EnumItemDTO;
import com.ep.databuilder.factory.FactoryDTOs.FactoryCreateDTO;
import com.ep.databuilder.factory.FactoryDTOs.FactoryDetailDTO;
import com.ep.databuilder.factory.FactoryDTOs.FactoryListVO;
import com.ep.databuilder.factory.FactoryDTOs.FactorySaveDTO;
import com.ep.databuilder.factory.FactoryDTOs.OutputDTO;
import com.ep.databuilder.factory.FactoryDTOs.ParamDTO;
import com.ep.databuilder.factory.FactoryDTOs.StepDTO;
import com.ep.databuilder.log.OperationLogService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FactoryService {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> DATA_TYPES = new HashSet<>(
            Arrays.asList("string", "int", "long", "boolean", "date", "datetime", "enum"));
    private static final Set<String> STEP_TYPES = new HashSet<>(Arrays.asList("API_CALL", "SQL_EXEC"));

    private final FactoryRepository factoryRepository;
    private final FactoryParamRepository paramRepository;
    private final FactoryStepRepository stepRepository;
    private final ObjectMapper objectMapper;
    private final OperationLogService operationLogService;

    public PageResult<FactoryListVO> list(String keyword, int page, int size) {
        String kw = (keyword == null || keyword.trim().isEmpty()) ? null : keyword.trim();
        Page<FactoryEntity> result = factoryRepository.search(kw,
                PageRequest.of(Math.max(page - 1, 0), size));
        return PageResult.of(result.getTotalElements(),
                result.getContent().stream().map(FactoryListVO::of).collect(Collectors.toList()));
    }

    @Transactional
    public Long create(FactoryCreateDTO dto) {
        if (!dto.getCode().contains(".")) {
            throw new BizException("工厂编码格式应为 {moduleCode}.{实体名}，如 user-center.user");
        }
        factoryRepository.findByCodeAndDeletedFalse(dto.getCode()).ifPresent(e -> {
            throw new BizException("工厂编码已存在：" + dto.getCode());
        });
        FactoryEntity entity = new FactoryEntity();
        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setOwner(dto.getOwner());
        factoryRepository.save(entity);
        operationLogService.record("FACTORY", entity.getId(), "CREATE", dto.getCode());
        return entity.getId();
    }

    public FactoryDetailDTO detail(Long id) {
        FactoryEntity entity = getFactory(id);
        FactoryDetailDTO dto = new FactoryDetailDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setOwner(entity.getOwner());
        dto.setEnabled(entity.getEnabled());
        dto.setPureSql(entity.getPureSql());
        dto.setLockVersion(entity.getLockVersion());
        dto.setParams(loadParams(id));
        dto.setSteps(loadSteps(id));
        return dto;
    }

    public List<ParamDTO> loadParams(Long factoryId) {
        List<ParamDTO> list = new ArrayList<>();
        for (FactoryParamEntity p : paramRepository.findByFactoryIdOrderBySortNo(factoryId)) {
            ParamDTO dto = new ParamDTO();
            dto.setName(p.getName());
            dto.setDataType(p.getDataType());
            dto.setDefaultValue(p.getDefaultValue());
            dto.setDescription(p.getDescription());
            dto.setSortNo(p.getSortNo());
            if (p.getEnums() != null && !p.getEnums().isEmpty()) {
                dto.setEnums(readJson(p.getEnums(), new TypeReference<List<EnumItemDTO>>() {
                }));
            }
            list.add(dto);
        }
        return list;
    }

    public List<StepDTO> loadSteps(Long factoryId) {
        List<StepDTO> list = new ArrayList<>();
        for (FactoryStepEntity s : stepRepository.findByFactoryIdOrderBySortNo(factoryId)) {
            StepDTO dto = new StepDTO();
            dto.setSortNo(s.getSortNo());
            dto.setStepType(s.getStepType());
            dto.setName(s.getName());
            dto.setConditionExpr(s.getConditionExpr());
            dto.setConfig(readTree(s.getConfig()));
            if (s.getOutputs() != null && !s.getOutputs().isEmpty()) {
                dto.setOutputs(readJson(s.getOutputs(), new TypeReference<List<OutputDTO>>() {
                }));
            }
            list.add(dto);
        }
        return list;
    }

    /** 全量保存：参数与步骤整体替换（删旧插新），乐观锁手动比对 */
    @Transactional
    public void save(Long id, FactorySaveDTO dto) {
        FactoryEntity entity = getFactory(id);
        if (!Objects.equals(entity.getLockVersion(), dto.getLockVersion())) {
            throw new BizException(1002, "工厂已被他人修改，请刷新后重试");
        }
        validate(dto);

        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setOwner(dto.getOwner());
        entity.setEnabled(dto.getEnabled() == null || dto.getEnabled());
        boolean pureSql = !dto.getSteps().isEmpty()
                && dto.getSteps().stream().noneMatch(s -> "API_CALL".equals(s.getStepType()));
        entity.setPureSql(pureSql);
        factoryRepository.save(entity);

        paramRepository.deleteByFactoryId(id);
        int sort = 0;
        for (ParamDTO p : dto.getParams()) {
            FactoryParamEntity pe = new FactoryParamEntity();
            pe.setFactoryId(id);
            pe.setName(p.getName());
            pe.setDataType(p.getDataType());
            pe.setDefaultValue(p.getDefaultValue());
            pe.setDescription(p.getDescription());
            pe.setEnums(p.getEnums() == null || p.getEnums().isEmpty() ? null : writeJson(p.getEnums()));
            pe.setSortNo(sort++);
            paramRepository.save(pe);
        }

        stepRepository.deleteByFactoryId(id);
        sort = 1;
        for (StepDTO s : dto.getSteps()) {
            FactoryStepEntity se = new FactoryStepEntity();
            se.setFactoryId(id);
            se.setSortNo(sort++);
            se.setStepType(s.getStepType());
            se.setName(s.getName());
            se.setConditionExpr(s.getConditionExpr());
            se.setConfig(s.getConfig() == null ? "{}" : s.getConfig().toString());
            se.setOutputs(s.getOutputs() == null || s.getOutputs().isEmpty() ? null : writeJson(s.getOutputs()));
            stepRepository.save(se);
        }
        operationLogService.record("FACTORY", id, "UPDATE", entity.getCode());
    }

    @Transactional
    public void delete(Long id) {
        FactoryEntity entity = getFactory(id);
        entity.setDeleted(Boolean.TRUE);
        factoryRepository.save(entity);
        operationLogService.record("FACTORY", id, "DELETE", entity.getCode());
    }

    public FactoryEntity getFactory(Long id) {
        FactoryEntity entity = factoryRepository.findById(id)
                .orElseThrow(() -> new BizException("工厂不存在：id=" + id));
        if (Boolean.TRUE.equals(entity.getDeleted())) {
            throw new BizException("工厂已删除：id=" + id);
        }
        return entity;
    }

    public FactoryEntity getByCode(String code) {
        return factoryRepository.findByCodeAndDeletedFalse(code)
                .orElseThrow(() -> new BizException("工厂不存在：" + code));
    }

    // ---- 保存校验 ----

    private void validate(FactorySaveDTO dto) {
        Set<String> varNames = new HashSet<>();
        for (ParamDTO p : dto.getParams()) {
            if (p.getName() == null || !IDENTIFIER.matcher(p.getName()).matches()) {
                throw new BizException("参数名不合法：" + p.getName() + "（字母/数字/下划线，字母开头）");
            }
            if (!varNames.add(p.getName())) {
                throw new BizException("参数名重复：" + p.getName());
            }
            if (p.getDataType() == null || !DATA_TYPES.contains(p.getDataType())) {
                throw new BizException("参数 " + p.getName() + " 类型不合法：" + p.getDataType());
            }
        }
        int idx = 1;
        for (StepDTO s : dto.getSteps()) {
            String label = "步骤" + idx + (s.getName() == null ? "" : "(" + s.getName() + ")");
            if (s.getStepType() == null || !STEP_TYPES.contains(s.getStepType())) {
                throw new BizException(label + " 类型不合法：" + s.getStepType() + "（M1 支持 API_CALL/SQL_EXEC）");
            }
            JsonNode config = s.getConfig();
            if (config == null || !config.isObject()) {
                throw new BizException(label + " 缺少配置");
            }
            if ("API_CALL".equals(s.getStepType())) {
                String apiCode = config.path("apiCode").asText("");
                if (apiCode.isEmpty() || !apiCode.contains(".")) {
                    throw new BizException(label + " 的 apiCode 必填且格式为 {moduleCode}.{接口名}");
                }
            } else {
                if (config.path("schemaCode").asText("").isEmpty()) {
                    throw new BizException(label + " 的 schemaCode 必填");
                }
                String sql = config.path("sql").asText("");
                if (sql.trim().isEmpty()) {
                    throw new BizException(label + " 的 SQL 必填");
                }
                SqlGuard.checkExecute(sql); // 模板态即做底线校验，问题提前到保存时暴露
                if (s.getOutputs() != null && !s.getOutputs().isEmpty()) {
                    throw new BizException(label + "：SQL_EXEC 步骤暂不支持输出提取（M2 的 CHECK 步骤支持查询取值）");
                }
            }
            for (OutputDTO o : s.getOutputs() == null ? new ArrayList<OutputDTO>() : s.getOutputs()) {
                if (o.getVar() == null || !IDENTIFIER.matcher(o.getVar()).matches()) {
                    throw new BizException(label + " 输出变量名不合法：" + o.getVar());
                }
                if (!varNames.add(o.getVar())) {
                    throw new BizException(label + " 输出变量与已有参数/输出重名：" + o.getVar());
                }
                if (o.getExpr() == null || o.getExpr().trim().isEmpty()) {
                    throw new BizException(label + " 输出变量 " + o.getVar() + " 缺少提取表达式（JSONPath）");
                }
            }
            idx++;
        }
    }

    // ---- JSON 工具 ----

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new BizException("JSON 解析失败：" + e.getMessage());
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (Exception e) {
            throw new BizException("JSON 解析失败：" + e.getMessage());
        }
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BizException("JSON 序列化失败：" + e.getMessage());
        }
    }
}
