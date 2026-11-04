package com.wovely.wovely.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/** RestTemplate for calls to the products/inventory services; adds the internal API key header. */
@Configuration
public class InternalClientConfig {

    public static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    @Bean
    RestTemplate restTemplate(@Value("${wovely.internal.apiKey:}") String apiKey) {
        RestTemplate rt = new RestTemplate();
        rt.getInterceptors().add((request, body, execution) -> {
            if (!apiKey.isBlank()) {
                request.getHeaders().set(INTERNAL_API_KEY_HEADER, apiKey);
            }
            return execution.execute(request, body);
        });
        return rt;
    }
}
