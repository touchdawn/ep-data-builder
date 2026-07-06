package com.ep.databuilder.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

@Configuration
public class HttpClientConfig {

    /** API_CALL 步骤用：4xx/5xx 不抛异常，由执行器按期望校验判定成败 */
    @Bean
    @Qualifier("stepRestTemplate")
    public RestTemplate stepRestTemplate(
            @Value("${ep.build.api-connect-timeout-ms:5000}") int connectTimeout,
            @Value("${ep.build.api-read-timeout-ms:30000}") int readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        RestTemplate template = new RestTemplate(factory);
        template.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        return template;
    }

    /** 平台间调用用（契约平台/猎户）：读超时 35s，比猎户侧 30s 执行超时略长 */
    @Bean
    @Qualifier("platformRestTemplate")
    public RestTemplate platformRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(35000);
        return new RestTemplate(factory);
    }
}
