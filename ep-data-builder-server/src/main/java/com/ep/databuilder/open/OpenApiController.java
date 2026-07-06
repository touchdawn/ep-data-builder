package com.ep.databuilder.open;

import com.ep.databuilder.buildrec.BuildDTOs.BuildDetailVO;
import com.ep.databuilder.buildrec.BuildEntity;
import com.ep.databuilder.buildrec.BuildQueryService;
import com.ep.databuilder.common.BizException;
import com.ep.databuilder.common.Result;
import com.ep.databuilder.engine.BuildService;
import com.ep.databuilder.env.EnvironmentEntity;
import com.ep.databuilder.env.EnvironmentRepository;
import com.ep.databuilder.factory.FactoryDTOs.ParamDTO;
import com.ep.databuilder.factory.FactoryEntity;
import com.ep.databuilder.factory.FactoryRepository;
import com.ep.databuilder.factory.FactoryService;
import com.ep.databuilder.security.OpenTokenInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** TestData Service 开放 API：工厂发现 + 造数执行（Header X-Builder-Token） */
@RestController
@RequestMapping("/open-api/v1")
@RequiredArgsConstructor
public class OpenApiController {

    private final FactoryRepository factoryRepository;
    private final FactoryService factoryService;
    private final EnvironmentRepository environmentRepository;
    private final BuildService buildService;
    private final BuildQueryService buildQueryService;
    private final ObjectMapper objectMapper;

    /** 工厂检索（LLM 造数推理入口）：按名称+功能描述模糊匹配 */
    @GetMapping("/factories/search")
    public Result<List<FactorySearchVO>> search(@RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "10") int topN) {
        String kw = (q == null || q.trim().isEmpty()) ? null : q.trim();
        List<FactorySearchVO> result = new ArrayList<>();
        for (FactoryEntity f : factoryRepository.searchEnabled(kw, PageRequest.of(0, Math.min(topN, 50)))) {
            result.add(toSearchVO(f));
        }
        return Result.ok(result);
    }

    /** 单工厂完整参数 schema（流水线按此组装 build 调用） */
    @GetMapping("/factories/{factoryCode}")
    public Result<FactorySearchVO> factory(@PathVariable String factoryCode) {
        FactoryEntity f = factoryService.getByCode(factoryCode);
        if (!Boolean.TRUE.equals(f.getEnabled())) {
            throw new BizException("工厂已停用：" + factoryCode);
        }
        return Result.ok(toSearchVO(f));
    }

    /** 造数（on-the-fly）：同步执行。失败时 code!=0，data 仍带 buildId 供排障 */
    @PostMapping("/builds")
    public Result<Map<String, Object>> build(@Valid @RequestBody OpenBuildRequest request,
                                             HttpServletRequest httpRequest) {
        FactoryEntity factory = factoryService.getByCode(request.getFactoryCode());
        EnvironmentEntity env = environmentRepository.findByCode(request.getEnvCode())
                .orElseThrow(() -> new BizException("环境不存在：" + request.getEnvCode()));
        String tokenName = (String) httpRequest.getAttribute(OpenTokenInterceptor.ATTR_TOKEN_NAME);

        BuildEntity build = buildService.execute(factory, env, request.getParams(), "OPEN_API", tokenName);

        Map<String, Object> data = new HashMap<>();
        data.put("buildId", build.getId());
        data.put("status", build.getStatus());
        data.put("outputs", parseOutputs(build.getOutputsJson()));
        data.put("durationMs", build.getDurationMs());
        if ("SUCCESS".equals(build.getStatus())) {
            return Result.ok(data);
        }
        Result<Map<String, Object>> result = Result.error(2001, build.getErrorMsg());
        result.setData(data);
        return result;
    }

    /** 执行轨迹：用例失败时归因"数据没造对 vs 系统 bug" */
    @GetMapping("/builds/{id}")
    public Result<BuildDetailVO> buildDetail(@PathVariable Long id) {
        return Result.ok(buildQueryService.detail(id));
    }

    private Object parseOutputs(String outputsJson) {
        if (outputsJson == null || outputsJson.isEmpty()) {
            return new HashMap<String, Object>();
        }
        try {
            return objectMapper.readTree(outputsJson);
        } catch (Exception e) {
            return outputsJson;
        }
    }

    private FactorySearchVO toSearchVO(FactoryEntity f) {
        FactorySearchVO vo = new FactorySearchVO();
        vo.setFactoryCode(f.getCode());
        vo.setName(f.getName());
        vo.setDescription(f.getDescription());
        vo.setPureSql(f.getPureSql());
        vo.setParams(factoryService.loadParams(f.getId()));
        return vo;
    }

    @Data
    public static class OpenBuildRequest {
        @NotBlank(message = "不能为空")
        private String factoryCode;
        @NotBlank(message = "不能为空")
        private String envCode;
        private Map<String, Object> params;
    }

    @Data
    public static class FactorySearchVO {
        private String factoryCode;
        private String name;
        private String description;
        private Boolean pureSql;
        private List<ParamDTO> params = new ArrayList<>();
        /** M2 套餐列表占位，格式先冻结 */
        private List<Object> recipes = new ArrayList<>();
    }
}
