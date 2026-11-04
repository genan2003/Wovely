package com.wovely.inventory.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/** RestTemplate for calls to other Wovely services; adds the internal API key header. */
@Configuration
public class InternalClientConfig {

    @Bean
    RestTemplate restTemplate(@Value("${wovely.internal.apiKey:}") String apiKey) {
        RestTemplate rt = new RestTemplate();
        rt.getInterceptors().add((request, body, execution) -> {
            if (!apiKey.isBlank()) {
                request.getHeaders().set(InternalApiKeyFilter.HEADER, apiKey);
            }
            return execution.execute(request, body);
        });
        return rt;
    }
}
