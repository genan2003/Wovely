package com.wovely.common.web;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.wovely.common.security.InternalApiKeyFilter;

/**
 * The one HTTP client for service-to-service calls: adds the internal API key, forwards the
 * correlation id and has timeouts (the old per-service RestTemplates could hang forever).
 */
@Configuration(proxyBeanMethods = false)
public class InternalHttpClientConfig {

    @Bean
    @ConditionalOnMissingBean(RestTemplate.class)
    RestTemplate restTemplate(@Value("${wovely.internal.apiKey:}") String apiKey,
            @Value("${wovely.internal.connectTimeoutMs:2000}") long connectMs,
            @Value("${wovely.internal.readTimeoutMs:5000}") long readMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectMs));
        factory.setReadTimeout(Duration.ofMillis(readMs));
        RestTemplate rt = new RestTemplate(factory);
        rt.getInterceptors().add((request, body, execution) -> {
            if (!apiKey.isBlank()) {
                request.getHeaders().set(InternalApiKeyFilter.HEADER, apiKey);
            }
            String correlation = CorrelationId.current();
            if (correlation != null) {
                request.getHeaders().set(CorrelationId.HEADER, correlation);
            }
            return execution.execute(request, body);
        });
        return rt;
    }
}
