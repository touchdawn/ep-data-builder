package com.ep.databuilder.contractclient;

import com.ep.databuilder.common.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 契约平台客户端：按 apiCode 拉取调用方式，本地缓存（TTL 可配）。
 * 契约平台不可达时：缓存未过期继续用；缓存无 + 拉取失败 → 明示失败，不猜。
 */
@Component
public class ContractClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String token;
    private final long ttlMillis;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public ContractClient(@Qualifier("platformRestTemplate") RestTemplate restTemplate,
                          @Value("${ep.contract.base-url:}") String baseUrl,
                          @Value("${ep.contract.token:}") String token,
                          @Value("${ep.contract.cache-ttl-minutes:10}") long ttlMinutes) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.token = token;
        this.ttlMillis = ttlMinutes * 60_000L;
    }

    public boolean configured() {
        return !baseUrl.isEmpty();
    }

    public ContractInvocation getInvocation(String apiCode) {
        if (!configured()) {
            throw new BizException("契约平台未配置（ep.contract.base-url 为空），API_CALL 步骤请使用 overrideUrl 兜底");
        }
        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(apiCode);
        if (cached != null && now - cached.fetchedAt < ttlMillis) {
            return cached.invocation;
        }
        try {
            ContractInvocation fresh = fetch(apiCode);
            cache.put(apiCode, new CacheEntry(fresh, now));
            return fresh;
        } catch (RestClientException e) {
            if (cached != null) {
                return cached.invocation; // 平台暂不可达，过期缓存降级续用
            }
            throw new BizException("契约平台不可达且无本地缓存：" + e.getMessage());
        }
    }

    public void evict(String apiCode) {
        cache.remove(apiCode);
    }

    private ContractInvocation fetch(String apiCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Contract-Token", token);
        ResponseEntity<JsonNode> resp = restTemplate.exchange(
                baseUrl + "/open-api/v1/apis/" + apiCode + "/contract",
                HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        JsonNode body = resp.getBody();
        if (body == null || body.path("code").asInt(-1) != 0) {
            throw new BizException("拉取契约失败[" + apiCode + "]："
                    + (body == null ? "空响应" : body.path("message").asText()));
        }
        JsonNode invocation = body.path("data").path("invocation");
        if (invocation.isMissingNode() || invocation.path("path").asText("").isEmpty()) {
            throw new BizException("契约缺少 invocation 定义：" + apiCode);
        }
        ContractInvocation result = new ContractInvocation();
        result.setMethod(invocation.path("method").asText("POST"));
        result.setPath(invocation.path("path").asText());
        result.setContentType(invocation.path("contentType").asText("application/json"));
        return result;
    }

    private static class CacheEntry {
        final ContractInvocation invocation;
        final long fetchedAt;

        CacheEntry(ContractInvocation invocation, long fetchedAt) {
            this.invocation = invocation;
            this.fetchedAt = fetchedAt;
        }
    }
}
