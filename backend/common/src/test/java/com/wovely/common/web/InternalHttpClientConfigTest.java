package com.wovely.common.web;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/** The shared client adds the internal key and forwards the correlation id (PLAN.md 4.3 / 4.6). */
class InternalHttpClientConfigTest {

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void addsApiKeyAndCorrelationId() {
        RestTemplate rt = new InternalHttpClientConfig().restTemplate("secret-key", 1000, 1000);
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        MDC.put(CorrelationId.MDC_KEY, "corr-12345678");
        server.expect(requestTo("http://svc/internal/x"))
            .andExpect(header("X-Internal-Api-Key", "secret-key"))
            .andExpect(header(CorrelationId.HEADER, "corr-12345678"))
            .andRespond(withSuccess());
        rt.getForObject("http://svc/internal/x", String.class);
        server.verify();
    }

    @Test
    void sendsNothingExtraWhenUnset() {
        RestTemplate rt = new InternalHttpClientConfig().restTemplate("", 1000, 1000);
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo("http://svc/x"))
            .andExpect(headerDoesNotExist("X-Internal-Api-Key"))
            .andExpect(headerDoesNotExist(CorrelationId.HEADER))
            .andRespond(withSuccess());
        rt.getForObject("http://svc/x", String.class);
        server.verify();
    }
}
