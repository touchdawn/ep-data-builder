package com.ep.databuilder.engine;

import com.ep.databuilder.buildrec.BuildEntity;
import com.ep.databuilder.buildrec.BuildRepository;
import com.ep.databuilder.buildrec.BuildStepLogEntity;
import com.ep.databuilder.buildrec.BuildStepLogRepository;
import com.ep.databuilder.common.BizException;
import com.ep.databuilder.engine.template.ConditionEvaluator;
import com.ep.databuilder.engine.template.TemplateEngine;
import com.ep.databuilder.env.EnvironmentEntity;
import com.ep.databuilder.factory.FactoryDTOs.ParamDTO;
import com.ep.databuilder.factory.FactoryDTOs.StepDTO;
import com.ep.databuilder.factory.FactoryEntity;
import com.ep.databuilder.factory.FactoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 造数执行引擎：参数合并 → 逐步执行（条件判定/执行/输出提取）→ 全轨迹留痕。
 * 刻意不加整体事务：步骤有外部副作用（API/目标库），失败即终止并如实呈现半成品轨迹。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuildService {

    private final FactoryService factoryService;
    private final ApiCallExecutor apiCallExecutor;
    private final SqlExecExecutor sqlExecExecutor;
    private final BuildRepository buildRepository;
    private final BuildStepLogRepository stepLogRepository;
    private final ObjectMapper objectMapper;

    public BuildEntity execute(FactoryEntity factory, EnvironmentEntity env,
                               Map<String, Object> callerParams, String source, String tokenName) {
        if (!Boolean.TRUE.equals(factory.getEnabled())) {
            throw new BizException("工厂已停用：" + factory.getCode());
        }
        List<ParamDTO> paramDefs = factoryService.loadParams(factory.getId());
        List<StepDTO> steps = factoryService.loadSteps(factory.getId());
        if (steps.isEmpty()) {
            throw new BizException("工厂没有配置任何步骤：" + factory.getCode());
        }

        Map<String, Object> vars = mergeParams(paramDefs, callerParams);

        BuildEntity build = new BuildEntity();
        build.setFactoryId(factory.getId());
        build.setEnvId(env.getId());
        build.setSource(source);
        build.setTokenName(tokenName);
        build.setParamsJson(toJson(vars));
        build.setStatus("RUNNING");
        buildRepository.save(build);

        long begin = System.currentTimeMillis();
        String error = null;
        for (StepDTO step : steps) {
            long stepBegin = System.currentTimeMillis();
            BuildStepLogEntity logEntity = new BuildStepLogEntity();
            logEntity.setBuildId(build.getId());
            logEntity.setSortNo(step.getSortNo());
            logEntity.setStepType(step.getStepType());
            logEntity.setStepName(step.getName());
            String label = "步骤" + step.getSortNo()
                    + (step.getName() == null || step.getName().isEmpty() ? "" : "(" + step.getName() + ")");
            try {
                if (!ConditionEvaluator.eval(step.getConditionExpr(), vars)) {
                    logEntity.setSkipped(Boolean.TRUE);
                    logEntity.setStatus("SKIPPED");
                    logEntity.setRequest("条件不满足：" + step.getConditionExpr());
                    continue;
                }
                StepOutcome outcome = executeStep(step, env.getId(), build.getId(), vars);
                vars.putAll(outcome.getOutputs());
                logEntity.setRequest(outcome.getRequestText());
                logEntity.setResponse(outcome.getResponseText());
                logEntity.setOutputsJson(outcome.getOutputs().isEmpty() ? null : toJson(outcome.getOutputs()));
                logEntity.setStatus("SUCCESS");
            } catch (BizException e) {
                logEntity.setStatus("FAILED");
                logEntity.setErrorMsg(e.getMessage());
                error = label + " 失败：" + e.getMessage();
            } catch (Exception e) {
                log.error("步骤执行未知异常 buildId={} step={}", build.getId(), step.getSortNo(), e);
                logEntity.setStatus("FAILED");
                logEntity.setErrorMsg("未知异常：" + e.getMessage());
                error = label + " 失败：未知异常 " + e.getMessage();
            } finally {
                logEntity.setDurationMs(System.currentTimeMillis() - stepBegin);
                stepLogRepository.save(logEntity);
            }
            if (error != null) {
                break;
            }
        }

        build.setDurationMs(System.currentTimeMillis() - begin);
        if (error == null) {
            build.setStatus("SUCCESS");
            build.setOutputsJson(toJson(vars)); // 输出 = 全量上下文（参数 + 各步骤提取值）
        } else {
            build.setStatus("FAILED");
            build.setErrorMsg(error);
            build.setOutputsJson(toJson(vars)); // 半成品上下文也留痕，便于排障
        }
        buildRepository.save(build);
        return build;
    }

    private StepOutcome executeStep(StepDTO step, Long envId, Long buildId, Map<String, Object> vars) {
        String traceId = "build-" + buildId + "-step-" + step.getSortNo();
        if ("API_CALL".equals(step.getStepType())) {
            return apiCallExecutor.execute(envId, step.getConfig(), step.getOutputs(), vars);
        }
        if ("SQL_EXEC".equals(step.getStepType())) {
            return sqlExecExecutor.execute(envId, step.getConfig(), vars, traceId);
        }
        throw new BizException("暂不支持的步骤类型：" + step.getStepType());
    }

    /** 参数合并：默认值 ←（M2 套餐）← 调用方，右侧覆盖左侧；未知参数拒绝 */
    private Map<String, Object> mergeParams(List<ParamDTO> paramDefs, Map<String, Object> callerParams) {
        Map<String, Object> vars = new LinkedHashMap<>();
        for (ParamDTO def : paramDefs) {
            Object value;
            if (callerParams != null && callerParams.containsKey(def.getName())) {
                value = convert(callerParams.get(def.getName()), def);
            } else if (def.getDefaultValue() != null && !def.getDefaultValue().isEmpty()) {
                // 默认值本身是文本模板（可用内置函数），渲染后再按类型转换
                value = convert(TemplateEngine.renderText(def.getDefaultValue(), vars), def);
            } else {
                value = null;
            }
            vars.put(def.getName(), value);
        }
        if (callerParams != null) {
            for (String key : callerParams.keySet()) {
                if (!vars.containsKey(key)) {
                    throw new BizException("未定义的参数：" + key + "（检查工厂参数定义）");
                }
            }
        }
        return vars;
    }

    private Object convert(Object raw, ParamDTO def) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw);
        try {
            switch (def.getDataType()) {
                case "int":
                case "long":
                    return raw instanceof Number ? ((Number) raw).longValue() : Long.parseLong(text.trim());
                case "boolean":
                    return raw instanceof Boolean ? raw : Boolean.parseBoolean(text.trim());
                case "enum":
                    if (def.getEnums() != null && !def.getEnums().isEmpty()
                            && def.getEnums().stream().noneMatch(e -> e.getValue().equals(text))) {
                        throw new BizException("参数 " + def.getName() + " 取值不在枚举范围：" + text);
                    }
                    return text;
                default:
                    // string/date/datetime 一律按文本传递（date/datetime 由目标库按字符串绑定）
                    return text;
            }
        } catch (NumberFormatException e) {
            throw new BizException("参数 " + def.getName() + " 需要 " + def.getDataType() + " 类型，实际值：" + text);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }
}
