package com.wovely.products.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/** PLAN.md 3.3 — products asks core whether a purchase is verified, failing closed. */
class PurchaseVerificationClientTest {

    static final String BASE = "http://core/internal";
    RestTemplate rest;
    MockRestServiceServer server;
    PurchaseVerificationClient client;

    @BeforeEach
    void setUp() {
        rest = new RestTemplate();
        server = MockRestServiceServer.bindTo(rest).build();
        client = new PurchaseVerificationClient(rest, BASE);
    }

    @Test
    void returnsCoresVerdict() {
        server.expect(requestTo(BASE + "/orders/verify-purchase?buyerId=b%201&productId=p-1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"verified\":true}", MediaType.APPLICATION_JSON));
        assertThat(client.hasReceived("b 1", "p-1")).isTrue();
        server.verify();
    }

    @Test
    void falseVerdictIsFalse() {
        server.expect(requestTo(BASE + "/orders/verify-purchase?buyerId=b&productId=p"))
            .andRespond(withSuccess("{\"verified\":false}", MediaType.APPLICATION_JSON));
        assertThat(client.hasReceived("b", "p")).isFalse();
    }

    @Test
    void coreErrorFailsClosed() {
        server.expect(requestTo(BASE + "/orders/verify-purchase?buyerId=b&productId=p")).andRespond(withServerError());
        assertThat(client.hasReceived("b", "p")).isFalse();
    }
}
