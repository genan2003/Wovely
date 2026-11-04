package com.wovely.wovely;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.wovely.common.error.ApiException;
import com.wovely.wovely.clients.InventoryClient;
import com.wovely.wovely.support.AbstractMongoIT;

/** PLAN.md 4.2 / 4.7 — core reserves at inventory; a release is never lost (real Mongo for the retry queue). */
class InventoryClientIT extends AbstractMongoIT {

    static final String BASE = "http://inventory/internal/inventory";

    @Autowired MongoTemplate mongo;
    RestTemplate rest;
    MockRestServiceServer server;
    InventoryClient client;
    final String product = "p-" + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        rest = new RestTemplate();
        server = MockRestServiceServer.bindTo(rest).build();
        client = new InventoryClient(rest, mongo, BASE);
        mongo.remove(new Query(), InventoryClient.PendingRelease.class);
    }

    private long queued() {
        return mongo.count(new Query(Criteria.where("productId").is(product)), InventoryClient.PendingRelease.class);
    }

    @Test
    void reserveMapsInventoryAnswers() {
        String url = BASE + "/stock/" + product + "/reserve";
        server.expect(requestTo(url)).andExpect(method(HttpMethod.POST))
            .andExpect(content().json("{\"sellerId\":\"s-1\",\"quantity\":2}"))
            .andRespond(withSuccess("{\"stockQuantity\":3}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.CONFLICT));
        server.expect(requestTo(url)).andRespond(withServiceUnavailable());

        client.reserve("s-1", product, 2);
        assertThatThrownBy(() -> client.reserve("s-1", product, 9)).isInstanceOf(ApiException.class)
            .extracting("status").isEqualTo(HttpStatus.CONFLICT);
        assertThatThrownBy(() -> client.reserve("s-1", product, 1)).extracting("status").isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        server.verify();
    }

    @Test
    void failedReleaseIsQueuedAndReplayedUntilDelivered() {
        String url = BASE + "/stock/" + product + "/release";
        server.expect(once(), requestTo(url)).andRespond(withServiceUnavailable());   // live call fails
        server.expect(once(), requestTo(url)).andRespond(withServiceUnavailable());   // 1st retry fails
        server.expect(once(), requestTo(url))                                         // 2nd retry works
            .andExpect(content().json("{\"sellerId\":\"s-1\",\"quantity\":2}"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.release("s-1", product, 2);
        assertThat(queued()).isEqualTo(1);
        client.retryPendingReleases();
        assertThat(queued()).as("still down, still queued").isEqualTo(1);
        client.retryPendingReleases();
        assertThat(queued()).isZero();
        server.verify();
    }

    @Test
    void releaseRejectedByInventoryIsParkedForAHuman() {
        String url = BASE + "/stock/" + product + "/release";
        server.expect(once(), requestTo(url)).andRespond(withServiceUnavailable());
        server.expect(once(), requestTo(url)).andRespond(withResourceNotFound());

        client.release("s-1", product, 1);
        client.retryPendingReleases();
        assertThat(queued()).isZero();
        assertThat(mongo.count(new Query(Criteria.where("productId").is(product)), "stock_release_failures")).isEqualTo(1);
        server.verify();
    }
}
