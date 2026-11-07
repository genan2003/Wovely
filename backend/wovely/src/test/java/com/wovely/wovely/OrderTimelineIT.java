package com.wovely.wovely;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.wovely.wovely.models.EOrderStatus;
import com.wovely.wovely.models.Order;
import com.wovely.wovely.models.OrderStatusChange;
import com.wovely.wovely.repository.OrderRepository;
import com.wovely.wovely.repository.UserRepository;
import com.wovely.wovely.support.AbstractMongoIT;
import com.wovely.wovely.support.Orders;
import com.wovely.wovely.support.TestTokens;

/** PLAN.md 7.1 against real MongoDB: the timeline is persisted with the status and survives legacy documents. */
class OrderTimelineIT extends AbstractMongoIT {

    @Autowired MockMvc mvc;
    @Autowired OrderRepository orders;
    @Autowired UserRepository users;
    @Autowired MongoTemplate mongo;

    private String id(String username) {
        return users.findByUsername(username).orElseThrow().getId(); // dev seed, matches TestTokens
    }

    /** Saved the way pre-7.1 code did: no status_history field at all. */
    private Order legacyPendingOrder() {
        Order o = Orders.order(null, id("testuser"), id("seller"), EOrderStatus.PENDING);
        o.setOrderNumber("ORD-" + UUID.randomUUID());
        o.setCreatedAt(Date.from(Instant.now().minus(Duration.ofDays(2))));
        o = orders.save(o);
        mongo.getCollection("orders").updateOne(new Document("_id", new org.bson.types.ObjectId(o.getId())),
                new Document("$unset", new Document("status_history", "")));
        return o;
    }

    private void sellerMoves(Order o, String to) throws Exception {
        mvc.perform(patch("/api/seller/orders/" + o.getId() + "/status")
                .header("Authorization", TestTokens.bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + to + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void legacyDocumentLoadsWithADerivedTimeline() throws Exception {
        Order o = legacyPendingOrder();
        assertThat(mongo.getCollection("orders").find(new Document("_id", new org.bson.types.ObjectId(o.getId())))
                .first()).doesNotContainKey("status_history");

        mvc.perform(get("/api/orders/" + o.getId()).header("Authorization", TestTokens.bearer(TestTokens.buyer())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timeline[*].status").value(contains("PENDING")))
            .andExpect(jsonPath("$.timeline[0].inferred").value(true));
    }

    @Test
    void transitionsArePersistedInOrderWithTheActorAndBackfillTheLegacyStart() throws Exception {
        Order o = legacyPendingOrder();
        sellerMoves(o, "CONFIRMED");
        sellerMoves(o, "PROCESSING");

        Order stored = orders.findById(o.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(EOrderStatus.PROCESSING);
        assertThat(stored.getStatusHistory())
                .extracting(OrderStatusChange::from, OrderStatusChange::to, OrderStatusChange::actorRole,
                        OrderStatusChange::actorId)
                .containsExactly(
                        tuple(null, EOrderStatus.PENDING, null, null),
                        tuple(EOrderStatus.PENDING, EOrderStatus.CONFIRMED, "SELLER", id("seller")),
                        tuple(EOrderStatus.CONFIRMED, EOrderStatus.PROCESSING, "SELLER", id("seller")));

        mvc.perform(get("/api/orders/" + o.getId()).header("Authorization", TestTokens.bearer(TestTokens.seller())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timeline[*].status").value(contains("PENDING", "CONFIRMED", "PROCESSING")))
            .andExpect(jsonPath("$.timeline[*].inferred").value(contains(true, false, false)))
            .andExpect(jsonPath("$.timeline[0].actorId").doesNotExist());
    }

    @Test
    void illegalTransitionLeavesTheStoredTimelineUntouched() throws Exception {
        Order o = legacyPendingOrder();
        mvc.perform(patch("/api/seller/orders/" + o.getId() + "/status")
                .header("Authorization", TestTokens.bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELIVERED\"}"))
            .andExpect(status().isConflict());
        assertThat(orders.findById(o.getId()).orElseThrow().getStatusHistory()).isEmpty();
    }
}
