package com.wovely.gateway;

import java.net.InetSocketAddress;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import reactor.core.publisher.Mono;

/**
 * Rate-limit buckets (Redis token bucket, see application.yml): per signed-in user, else per client IP.
 * {@code X-Real-IP} is trusted because nginx overwrites it; the gateway port is only published on
 * 127.0.0.1 for tooling, never to the internet.
 */
@Configuration
public class RateLimitConfig {

    @Bean
    @Primary
    KeyResolver userOrIpKeyResolver() {
        return exchange -> {
            Object uid = exchange.getAttribute(EdgeFilter.USER_ID_ATTR);
            if (uid != null) {
                return Mono.just("user:" + uid);
            }
            String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return Mono.just("ip:" + realIp.trim());
            }
            InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
            return Mono.just("ip:" + (remote != null && remote.getAddress() != null ? remote.getAddress().getHostAddress() : "unknown"));
        };
    }
}
