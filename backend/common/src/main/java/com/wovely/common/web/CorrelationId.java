package com.wovely.common.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Request correlation (PLAN.md 4.6): reuses a valid incoming {@value #HEADER} (set by the gateway or a
 * calling service) or creates one, puts it in the SLF4J MDC as {@value #MDC_KEY}, echoes it on the
 * response and lets {@link InternalHttpClientConfig} forward it on service-to-service calls.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationId extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    /** Accept only harmless ids so a caller can't inject log lines. */
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String id = incoming != null && SAFE.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** The current request's id, or {@code null} outside a request. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
