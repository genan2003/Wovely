package com.wovely.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** PLAN.md 4.6 — correlation ids are reused when safe, generated otherwise, and never leak between requests. */
class CorrelationIdTest {

    final CorrelationId filter = new CorrelationId();

    private String run(String incoming, AtomicReference<String> seenInChain) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (incoming != null) req.addHeader(CorrelationId.HEADER, incoming);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, (rq, rs) -> seenInChain.set(CorrelationId.current()));
        return res.getHeader(CorrelationId.HEADER);
    }

    @Test
    void reusesAValidIncomingId() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        assertThat(run("gw-1234abcd", seen)).isEqualTo("gw-1234abcd");
        assertThat(seen.get()).isEqualTo("gw-1234abcd");
        assertThat(CorrelationId.current()).as("MDC cleared after the request").isNull();
    }

    @Test
    void replacesMissingOrUnsafeIds() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        for (String bad : new String[] { null, "short", "evil\nINFO forged log line", "x".repeat(65) }) {
            String id = run(bad, seen);
            assertThat(id).as(String.valueOf(bad)).matches("[0-9a-f-]{36}");
            assertThat(seen.get()).isEqualTo(id);
        }
    }
}
