package com.ep.databuilder.env;

import com.ep.databuilder.common.AesUtil;
import com.ep.databuilder.common.BizException;
import com.ep.databuilder.engine.channel.DataChannelRouter;
import com.ep.databuilder.engine.ds.DynamicDataSourceManager;
import com.ep.databuilder.env.EnvDTOs.DatasourceSaveDTO;
import com.ep.databuilder.env.EnvDTOs.EndpointSaveDTO;
import com.ep.databuilder.env.EnvDTOs.EnvSaveDTO;
import com.ep.databuilder.log.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EnvService {

    private final EnvironmentRepository environmentRepository;
    private final ModuleEndpointRepository endpointRepository;
    private final DatasourceRepository datasourceRepository;
    private final AesUtil aesUtil;
    private final DynamicDataSourceManager dataSourceManager;
    private final DataChannelRouter channelRouter;
    private final OperationLogService operationLogService;

    // ---- 环境 ----

    public List<EnvironmentEntity> listEnvs() {
        return environmentRepository.findAll();
    }

    @Transactional
    public Long createEnv(EnvSaveDTO dto) {
        environmentRepository.findByCode(dto.getCode()).ifPresent(e -> {
            throw new BizException("环境编码已存在：" + dto.getCode());
        });
        EnvironmentEntity entity = new EnvironmentEntity();
        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        environmentRepository.save(entity);
        operationLogService.record("ENV", entity.getId(), "CREATE", dto.getCode());
        return entity.getId();
    }

    @Transactional
    public void updateEnv(Long id, EnvSaveDTO dto) {
        EnvironmentEntity entity = getEnv(id);
        environmentRepository.findByCode(dto.getCode()).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new BizException("环境编码已存在：" + dto.getCode());
            }
        });
        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        environmentRepository.save(entity);
        operationLogService.record("ENV", id, "UPDATE", dto.getCode());
    }

    @Transactional
    public void deleteEnv(Long id) {
        EnvironmentEntity entity = getEnv(id);
        if (!endpointRepository.findByEnvIdOrderByModuleCode(id).isEmpty()
                || !datasourceRepository.findByEnvIdOrderBySchemaCode(id).isEmpty()) {
            throw new BizException("环境下存在端点或数据源，不允许删除");
        }
        environmentRepository.delete(entity);
        operationLogService.record("ENV", id, "DELETE", entity.getCode());
    }

    public EnvironmentEntity getEnv(Long id) {
        return environmentRepository.findById(id)
                .orElseThrow(() -> new BizException("环境不存在：id=" + id));
    }

    // ---- 模块端点 ----

    public List<ModuleEndpointEntity> listEndpoints(Long envId) {
        return endpointRepository.findByEnvIdOrderByModuleCode(envId);
    }

    @Transactional
    public Long createEndpoint(Long envId, EndpointSaveDTO dto) {
        getEnv(envId);
        endpointRepository.findByEnvIdAndModuleCode(envId, dto.getModuleCode()).ifPresent(e -> {
            throw new BizException("该环境下模块端点已存在：" + dto.getModuleCode());
        });
        ModuleEndpointEntity entity = new ModuleEndpointEntity();
        entity.setEnvId(envId);
        apply(entity, dto);
        endpointRepository.save(entity);
        operationLogService.record("ENV", envId, "ENDPOINT_CREATE", dto.getModuleCode());
        return entity.getId();
    }

    @Transactional
    public void updateEndpoint(Long id, EndpointSaveDTO dto) {
        ModuleEndpointEntity entity = endpointRepository.findById(id)
                .orElseThrow(() -> new BizException("端点不存在：id=" + id));
        endpointRepository.findByEnvIdAndModuleCode(entity.getEnvId(), dto.getModuleCode()).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new BizException("该环境下模块端点已存在：" + dto.getModuleCode());
            }
        });
        apply(entity, dto);
        endpointRepository.save(entity);
        operationLogService.record("ENV", entity.getEnvId(), "ENDPOINT_UPDATE", dto.getModuleCode());
    }

    @Transactional
    public void deleteEndpoint(Long id) {
        ModuleEndpointEntity entity = endpointRepository.findById(id)
                .orElseThrow(() -> new BizException("端点不存在：id=" + id));
        endpointRepository.delete(entity);
        operationLogService.record("ENV", entity.getEnvId(), "ENDPOINT_DELETE", entity.getModuleCode());
    }

    private static void apply(ModuleEndpointEntity entity, EndpointSaveDTO dto) {
        entity.setModuleCode(dto.getModuleCode());
        entity.setBaseUrl(trimTrailingSlash(dto.getBaseUrl()));
        entity.setHeaders(dto.getHeaders());
    }

    private static String trimTrailingSlash(String url) {
        String s = url.trim();
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    // ---- 数据源 ----

    public List<DatasourceEntity> listDatasources(Long envId) {
        return datasourceRepository.findByEnvIdOrderBySchemaCode(envId);
    }

    @Transactional
    public Long createDatasource(Long envId, DatasourceSaveDTO dto) {
        getEnv(envId);
        datasourceRepository.findByEnvIdAndSchemaCode(envId, dto.getSchemaCode()).ifPresent(e -> {
            throw new BizException("该环境下数据源已存在：" + dto.getSchemaCode());
        });
        validateChannel(dto);
        DatasourceEntity entity = new DatasourceEntity();
        entity.setEnvId(envId);
        entity.setSchemaCode(dto.getSchemaCode());
        entity.setDbType(dto.getDbType());
        entity.setChannel(dto.getChannel().toUpperCase());
        entity.setJdbcUrl(dto.getJdbcUrl());
        entity.setUsername(dto.getUsername());
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            entity.setPasswordEnc(aesUtil.encrypt(dto.getPassword()));
        }
        datasourceRepository.save(entity);
        operationLogService.record("DATASOURCE", entity.getId(), "CREATE",
                dto.getSchemaCode() + " channel=" + entity.getChannel());
        return entity.getId();
    }

    @Transactional
    public void updateDatasource(Long id, DatasourceSaveDTO dto) {
        DatasourceEntity entity = getDatasource(id);
        datasourceRepository.findByEnvIdAndSchemaCode(entity.getEnvId(), dto.getSchemaCode()).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new BizException("该环境下数据源已存在：" + dto.getSchemaCode());
            }
        });
        validateChannel(dto);
        String oldChannel = entity.getChannel();
        entity.setSchemaCode(dto.getSchemaCode());
        entity.setDbType(dto.getDbType());
        entity.setChannel(dto.getChannel().toUpperCase());
        entity.setJdbcUrl(dto.getJdbcUrl());
        entity.setUsername(dto.getUsername());
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            entity.setPasswordEnc(aesUtil.encrypt(dto.getPassword()));
        }
        datasourceRepository.save(entity);
        // 通道切换是需要留痕的显式动作（设计文档 §12.2）
        String detail = dto.getSchemaCode()
                + (oldChannel.equalsIgnoreCase(dto.getChannel()) ? ""
                : " channel:" + oldChannel + "->" + dto.getChannel().toUpperCase());
        operationLogService.record("DATASOURCE", id, "UPDATE", detail);
    }

    @Transactional
    public void deleteDatasource(Long id) {
        DatasourceEntity entity = getDatasource(id);
        datasourceRepository.delete(entity);
        dataSourceManager.evict(id);
        operationLogService.record("DATASOURCE", id, "DELETE", entity.getSchemaCode());
    }

    public DatasourceEntity getDatasource(Long id) {
        return datasourceRepository.findById(id)
                .orElseThrow(() -> new BizException("数据源不存在：id=" + id));
    }

    /** 连通性测试：DIRECT 测 JDBC 连接；HUNTER 跑一条 SELECT 1 验证接口与 scope */
    public void testConnection(Long id) {
        DatasourceEntity ds = getDatasource(id);
        if ("DIRECT".equalsIgnoreCase(ds.getChannel())) {
            try (java.sql.Connection conn = dataSourceManager.dataSource(ds).getConnection()) {
                if (!conn.isValid(5)) {
                    throw new BizException("连接无效");
                }
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                throw new BizException("连接失败：" + e.getMessage());
            }
        } else {
            channelRouter.route(ds).query(ds, "SELECT 1", Collections.emptyList(), 1, "conn-test");
        }
    }

    private static void validateChannel(DatasourceSaveDTO dto) {
        String channel = dto.getChannel().toUpperCase();
        if (!"HUNTER".equals(channel) && !"DIRECT".equals(channel)) {
            throw new BizException("通道只能是 HUNTER 或 DIRECT");
        }
        if ("DIRECT".equals(channel) && (dto.getJdbcUrl() == null || dto.getJdbcUrl().trim().isEmpty())) {
            throw new BizException("DIRECT 通道必须填写 JDBC URL");
        }
    }
}
