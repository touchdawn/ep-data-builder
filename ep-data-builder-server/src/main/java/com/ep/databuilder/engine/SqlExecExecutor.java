package com.ep.databuilder.engine;

import com.ep.databuilder.common.BizException;
import com.ep.databuilder.engine.channel.DataChannelRouter;
import com.ep.databuilder.engine.channel.ExecResult;
import com.ep.databuilder.engine.template.RenderedSql;
import com.ep.databuilder.engine.template.TemplateEngine;
import com.ep.databuilder.env.DatasourceEntity;
import com.ep.databuilder.env.DatasourceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** SQL_EXEC 步骤：模板渲染（值全部走绑定参数）→ DataChannel 执行 → 影响行数校验 */
@Component
@RequiredArgsConstructor
public class SqlExecExecutor {

    private final DatasourceRepository datasourceRepository;
    private final DataChannelRouter channelRouter;

    public StepOutcome execute(Long envId, JsonNode config, Map<String, Object> vars, String traceId) {
        String schemaCode = config.path("schemaCode").asText("");
        DatasourceEntity ds = datasourceRepository.findByEnvIdAndSchemaCode(envId, schemaCode)
                .orElseThrow(() -> new BizException("环境未配置数据源：" + schemaCode + "（环境管理→数据源）"));

        RenderedSql rendered = TemplateEngine.renderSql(config.path("sql").asText(), vars);

        StepOutcome outcome = new StepOutcome();
        List<String> paramTexts = new ArrayList<>();
        for (Object p : rendered.getParams()) {
            paramTexts.add(TemplateEngine.asText(p));
        }
        outcome.setRequestText("[" + ds.getChannel() + "] " + schemaCode + "\n"
                + rendered.getSql() + "\nparams: " + paramTexts);

        ExecResult result = channelRouter.route(ds)
                .execute(ds, rendered.getSql(), rendered.getParams(), traceId);
        outcome.setResponseText("affectedRows=" + result.getAffectedRows()
                + (result.getAuditId() == null ? "" : ", hunterAuditId=" + result.getAuditId()));

        if (config.hasNonNull("expectAffectedRows")) {
            int expected = config.path("expectAffectedRows").asInt();
            if (result.getAffectedRows() != expected) {
                throw new BizException("影响行数不符合期望：期望 " + expected + "，实际 " + result.getAffectedRows());
            }
        }
        return outcome;
    }
}
