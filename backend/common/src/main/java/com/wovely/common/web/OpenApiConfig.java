package com.wovely.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * OpenAPI description shared by every service (PLAN.md 4.5): served at {@code /v3/api-docs}, browsable at
 * {@code /swagger-ui.html}. Documents the bearer JWT (from {@code POST /api/auth/signin}) and the
 * internal API key so "Try it out" works; the public server is the gateway.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    public static final String BEARER = "bearerJwt";
    public static final String INTERNAL_KEY = "internalApiKey";

    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    OpenAPI wovelyOpenApi(@Value("${spring.application.name:wovely}") String service,
            @Value("${wovely.openapi.public-url:http://localhost:8090}") String gatewayUrl) {
        return new OpenAPI()
                .info(new Info().title("Wovely – " + service + " API").version("v1")
                        .description("Handmade-goods marketplace. Errors use {status, error, message, path, timestamp}; "
                                + "/internal/** endpoints are service-to-service only and are not reachable through the gateway."))
                .addServersItem(new Server().url(gatewayUrl).description("Gateway (browser entry point)"))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")
                                .bearerFormat("JWT").description("Access token from POST /api/auth/signin (15 min)"))
                        .addSecuritySchemes(INTERNAL_KEY, new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER).name("X-Internal-Api-Key")
                                .description("Service-to-service calls on /internal/** only")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
