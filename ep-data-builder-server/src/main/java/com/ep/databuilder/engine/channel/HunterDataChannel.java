package com.ep.databuilder.engine.channel;

import com.ep.databuilder.common.BizException;
import com.ep.databuilder.common.SqlGuard;
import com.ep.databuilder.env.DatasourceEntity;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HUNTER 主通道：调测试猎户「数据执行开放接口」（规范见 docs/hunter-open-exec-api.md）。
 * 传输约定：Timestamp 序列化为 "yyyy-MM-dd HH:mm:ss" 字符串（看门狗侧按字符串绑定 datetime 列）。
 * 猎户不可达/未配置时显式失败，不自动降级 DIRECT——切换通道必须是显式配置动作。
 */
@Component
public class HunterDataChannel implements DataChannel {

    private static final String TOKEN_HEADER = "X-Hunter-Service-Token";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String token;

    public HunterDataChannel(@Qualifier("platformRestTemplate") RestTemplate restTemplate,
                             @Value("${ep.hunter.base-url:}") String baseUrl,
                             @Value("${ep.hunter.token:}") String token) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.token = token;
    }

    @Override
    public QueryResult query(DatasourceEntity ds, String sql, List<Object> params, int maxRows, String traceId) {
        SqlGuard.checkQuery(sql);
        Map<String, Object> body = baseBody(ds, sql, params, traceId);
        body.put("maxRows", maxRows <= 0 ? 100 : Math.min(maxRows, 1000));
        JsonNode data = post("/hunter-open/v1/query", body, ds);
        QueryResult result = new QueryResult();
        List<String> columns = new ArrayList<>();
        for (JsonNode c : data.path("columns")) {
            columns.add(c.asText());
        }
        List<List<Object>> rows = new ArrayList<>();
        for (JsonNode r : data.path("rows")) {
            List<Object> row = new ArrayList<>();
            for (JsonNode cell : r) {
                row.add(cell.isNull() ? null : (cell.isNumber() ? cell.numberValue() : cell.asText()));
            }
            rows.add(row);
        }
        result.setColumns(columns);
        result.setRows(rows);
        result.setAuditId(data.path("auditId").isNumber() ? data.path("auditId").asLong() : null);
        result.setTruncated(data.path("truncated").asBoolean(false));
        return result;
    }

    @Override
    public ExecResult execute(DatasourceEntity ds, String sql, List<Object> params, String traceId) {
        SqlGuard.checkExecute(sql);
        Map<String, Object> body = baseBody(ds, sql, params, traceId);
        JsonNode data = post("/hunter-open/v1/execute", body, ds);
        return new ExecResult(data.path("affectedRows").asInt(),
                data.path("auditId").isNumber() ? data.path("auditId").asLong() : null);
    }

    private Map<String, Object> baseBody(DatasourceEntity ds, String sql, List<Object> params, String traceId) {
        Map<String, Object> body = new HashMap<>();
        body.put("schemaCode", ds.getSchemaCode());
        body.put("sql", sql);
        List<Object> transportParams = new ArrayList<>();
        for (Object p : params) {
            transportParams.add(p instanceof Timestamp
                    ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((Timestamp) p)
                    : p);
        }
        body.put("params", transportParams);
        body.put("traceId", traceId);
        return body;
    }

    private JsonNode post(String path, Map<String, Object> body, DatasourceEntity ds) {
        if (baseUrl.isEmpty()) {
            throw new BizException("HUNTER 通道未配置（ep.hunter.base-url 为空）。猎户开放接口就绪前，"
                    + "请将数据源 " + ds.getSchemaCode() + " 切为 DIRECT 通道");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(TOKEN_HEADER, token);
        JsonNode resp;
        try {
            resp = restTemplate.postForObject(baseUrl + path, new HttpEntity<>(body, headers), JsonNode.class);
        } catch (RestClientException e) {
            throw new BizException("猎户开放接口不可达：" + e.getMessage()
                    + "（不做自动降级，如需应急请显式将数据源切为 DIRECT 通道）");
        }
        if (resp == null) {
            throw new BizException("猎户开放接口返回空响应");
        }
        if (resp.path("code").asInt(-1) != 0) {
            throw new BizException("猎户执行失败[" + resp.path("code").asText() + "]："
                    + resp.path("message").asText());
        }
        return resp.path("data");
    }
}
