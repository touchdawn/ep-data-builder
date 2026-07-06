package com.ep.databuilder.engine;

import com.ep.databuilder.common.BizException;
import com.ep.databuilder.contractclient.ContractClient;
import com.ep.databuilder.contractclient.ContractInvocation;
import com.ep.databuilder.engine.template.TemplateEngine;
import com.ep.databuilder.env.ModuleEndpointEntity;
import com.ep.databuilder.env.ModuleEndpointRepository;
import com.ep.databuilder.factory.FactoryDTOs.OutputDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * API_CALL 步骤：按 apiCode 从契约平台取调用方式（overrideMethod/overridePath 为脱契约兜底），
 * 模块端点表拼 URL，模板渲染后 RestTemplate 执行，期望校验 + JSONPath 输出提取。
 */
@Component
@RequiredArgsConstructor
public class ApiCallExecutor {

    private static final Pattern PATH_VAR = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private static final int TRACE_BODY_LIMIT = 4000;

    private final ModuleEndpointRepository endpointRepository;
    private final ContractClient contractClient;
    private final ObjectMapper objectMapper;
    @Qualifier("stepRestTemplate")
    private final RestTemplate stepRestTemplate;

    public StepOutcome execute(Long envId, JsonNode config, List<OutputDTO> outputs, Map<String, Object> vars) {
        String apiCode = config.path("apiCode").asText("");
        int dot = apiCode.indexOf('.');
        if (dot <= 0) {
            throw new BizException("apiCode 格式不合法：" + apiCode);
        }
        String moduleCode = apiCode.substring(0, dot);
        ModuleEndpointEntity endpoint = endpointRepository.findByEnvIdAndModuleCode(envId, moduleCode)
                .orElseThrow(() -> new BizException("环境未配置模块端点：" + moduleCode + "（环境管理→模块端点）"));

        // 调用方式：override 优先，否则拉契约
        String method;
        String path;
        String contentType = "application/json";
        String overridePath = config.path("overridePath").asText("");
        if (!overridePath.isEmpty()) {
            method = config.path("overrideMethod").asText("POST").toUpperCase();
            path = overridePath;
        } else {
            ContractInvocation invocation = contractClient.getInvocation(apiCode);
            method = invocation.getMethod().toUpperCase();
            path = invocation.getPath();
            if (invocation.getContentType() != null && !invocation.getContentType().isEmpty()) {
                contentType = invocation.getContentType();
            }
        }

        // 路径渲染：先 ${} 模板，再 {pathVar} 契约风格占位
        path = TemplateEngine.renderText(path, vars);
        path = replacePathVars(path, vars);
        StringBuilder url = new StringBuilder(endpoint.getBaseUrl()).append(path);

        // query 参数
        JsonNode queryTemplate = config.path("queryTemplate");
        if (queryTemplate.isObject() && queryTemplate.size() > 0) {
            StringBuilder qs = new StringBuilder();
            Iterator<String> names = queryTemplate.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                String value = TemplateEngine.renderText(queryTemplate.path(name).asText(), vars);
                if (qs.length() > 0) {
                    qs.append('&');
                }
                qs.append(name).append('=').append(urlEncode(value));
            }
            url.append(url.indexOf("?") >= 0 ? '&' : '?').append(qs);
        }

        // headers：端点公共 Header + 步骤 Header 模板
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        mergeHeaders(headers, endpoint.getHeaders(), vars);
        JsonNode headerTemplate = config.path("headerTemplate");
        if (headerTemplate.isObject()) {
            Iterator<String> names = headerTemplate.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                headers.set(name, TemplateEngine.renderText(headerTemplate.path(name).asText(), vars));
            }
        }

        // body
        String body = null;
        String bodyTemplate = config.path("bodyTemplate").asText("");
        if (!bodyTemplate.trim().isEmpty() && !"GET".equals(method)) {
            body = TemplateEngine.renderText(bodyTemplate, vars);
        }

        StepOutcome outcome = new StepOutcome();
        outcome.setRequestText(method + " " + url + "\nheaders: " + maskHeaders(headers)
                + (body == null ? "" : "\nbody: " + body));

        ResponseEntity<String> response;
        try {
            response = stepRestTemplate.exchange(url.toString(), HttpMethod.valueOf(method),
                    new HttpEntity<>(body, headers), String.class);
        } catch (RestClientException e) {
            throw new BizException("接口调用失败：" + method + " " + url + " → " + e.getMessage());
        }
        int status = response.getStatusCodeValue();
        String respBody = response.getBody() == null ? "" : response.getBody();
        outcome.setResponseText("HTTP " + status + "\n" + truncate(respBody));

        // 期望校验
        int expectStatus = config.path("expectHttpStatus").asInt(200);
        if (status != expectStatus) {
            throw new BizException("HTTP 状态码不符合期望：期望 " + expectStatus + "，实际 " + status
                    + "，响应：" + truncate(respBody));
        }
        JsonNode asserts = config.path("asserts");
        if (asserts.isArray()) {
            for (JsonNode rule : asserts) {
                checkAssert(rule, respBody);
            }
        }

        // 输出提取
        if (outputs != null) {
            for (OutputDTO output : outputs) {
                Object value;
                try {
                    value = JsonPath.read(respBody, output.getExpr());
                } catch (Exception e) {
                    throw new BizException("输出变量 " + output.getVar() + " 提取失败（" + output.getExpr()
                            + "）：" + e.getMessage());
                }
                outcome.getOutputs().put(output.getVar(), value);
            }
        }
        return outcome;
    }

    private void checkAssert(JsonNode rule, String respBody) {
        String expr = rule.path("expr").asText();
        String op = rule.path("op").asText("==");
        String expected = rule.path("value").asText();
        Object actual;
        try {
            actual = JsonPath.read(respBody, expr);
        } catch (Exception e) {
            throw new BizException("断言表达式取值失败（" + expr + "）：" + e.getMessage());
        }
        String actualText = actual == null ? "" : String.valueOf(actual);
        boolean pass;
        switch (op) {
            case "==":
                pass = actualText.equals(expected);
                break;
            case "!=":
                pass = !actualText.equals(expected);
                break;
            case "contains":
                pass = actualText.contains(expected);
                break;
            default:
                throw new BizException("不支持的断言操作符：" + op);
        }
        if (!pass) {
            throw new BizException("断言失败：" + expr + " " + op + " " + expected + "，实际值：" + actualText);
        }
    }

    private void mergeHeaders(HttpHeaders headers, String headersJson, Map<String, Object> vars) {
        if (headersJson == null || headersJson.trim().isEmpty()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(headersJson);
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                headers.set(name, TemplateEngine.renderText(node.path(name).asText(), vars));
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("模块端点公共 Header 不是合法 JSON：" + e.getMessage());
        }
    }

    private static String replacePathVars(String path, Map<String, Object> vars) {
        Matcher m = PATH_VAR.matcher(path);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(1);
            if (!vars.containsKey(name)) {
                throw new BizException("路径变量 {" + name + "} 无对应参数/输出变量");
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(urlEncode(TemplateEngine.asText(vars.get(name)))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String maskHeaders(HttpHeaders headers) {
        StringBuilder sb = new StringBuilder("{");
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String key = entry.getKey();
            String lower = key.toLowerCase();
            boolean sensitive = lower.contains("authorization") || lower.contains("token")
                    || lower.contains("password") || lower.contains("secret");
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(key).append('=').append(sensitive ? "***" : String.join(",", entry.getValue()));
        }
        return sb.append('}').toString();
    }

    private static String truncate(String s) {
        return s.length() <= TRACE_BODY_LIMIT ? s : s.substring(0, TRACE_BODY_LIMIT) + "...(截断)";
    }
}
