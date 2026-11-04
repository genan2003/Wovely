package com.wovely.common;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

import com.wovely.common.error.GlobalExceptionHandler;
import com.wovely.common.web.CorrelationId;
import com.wovely.common.web.CorsConfig;
import com.wovely.common.web.InternalHttpClientConfig;

/** Loaded automatically by every service that depends on wovely-common (PLAN.md 4.3). */
@AutoConfiguration
@Import({ GlobalExceptionHandler.class, CorsConfig.class, InternalHttpClientConfig.class, com.wovely.common.web.OpenApiConfig.class })
public class WovelyCommonAutoConfiguration {

    @Bean
    FilterRegistrationBean<CorrelationId> correlationIdFilter() {
        FilterRegistrationBean<CorrelationId> reg = new FilterRegistrationBean<>(new CorrelationId());
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.addUrlPatterns("/*");
        return reg;
    }
}
