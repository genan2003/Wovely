package com.wovely.wovely;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.wovely.wovely.config.MoneyCentsBackfill;
import com.wovely.wovely.models.Order;
import com.wovely.wovely.repository.UserRepository;
import com.wovely.wovely.support.AbstractMongoIT;
import com.wovely.wovely.support.TestTokens;

/** PLAN.md 7.5 against real MongoDB: orders and returns stored with doubles become whole cents exactly once. */
class MoneyCentsBackfillIT extends AbstractMongoIT {

    @Autowired MongoTemplate mongo;
    @Autowired MoneyCentsBackfill backfill;
    @Autowired UserRepository users;
    @Autowired MockMvc mvc;

    private Document raw(String collection, ObjectId id) {
        return mongo.getCollection(collection).find(new Document("_id", id)).first();
    }

    /** As pre-7.5 code saved it: doubles everywhere, including a float-drifted refund. */
    private ObjectId legacyOrder(String buyerId) {
        ObjectId id = new ObjectId();
        mongo.getCollection("orders").insertOne(new Document("_id", id)
                .append("order_number", "ORD-LEGACY-" + UUID.randomUUID()).append("buyer_id", buyerId)
                .append("seller_id", "s-legacy").append("status", "DELIVERED").append("created_at", new Date())
                .append("total_amount", 37.5).append("deliveryFee", 10.0)
                .append("refunded_amount", 0.1 + 0.2) // 0.30000000000000004
                .append("items", List.of(
                        new Document("productId", "p-1").append("productName", "Basket").append("quantity", 2).append("price", 13.75),
                        new Document("productId", "p-2").append("productName", "Coaster").append("quantity", 1).append("price", 0.0))));
        return id;
    }

    @Test
    void legacyOrderAmountsBecomeCentsAndTheApiShowsBothForms() throws Exception {
        ObjectId id = legacyOrder(users.findByUsername("testuser").orElseThrow().getId());

        assertThat(backfill.backfill()).isGreaterThanOrEqualTo(1);

        Document doc = raw("orders", id);
        assertThat(doc).containsEntry("total_cents", 3750L).containsEntry("deliveryFeeCents", 1000L)
                .containsEntry("refunded_cents", 30L)
                .doesNotContainKeys("total_amount", "deliveryFee", "refunded_amount");
        assertThat(doc.getList("items", Document.class))
                .extracting(d -> d.get("priceCents"), d -> d.containsKey("price"))
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1375L, false), org.assertj.core.groups.Tuple.tuple(0L, false));
        assertThat(doc.getList("items", Document.class).get(0)).containsEntry("productName", "Basket").containsEntry("quantity", 2);

        Order order = mongo.findById(id, Order.class);
        assertThat(order.getTotalCents()).isEqualTo(order.itemsTotalCents() + order.getDeliveryFeeCents());

        mvc.perform(get("/api/orders/" + id.toHexString()).header("Authorization", TestTokens.bearer(TestTokens.buyer())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCents").value(3750))
            .andExpect(jsonPath("$.totalAmount").value(37.5))
            .andExpect(jsonPath("$.refundedAmount").value(0.3))
            .andExpect(jsonPath("$.items[0].priceCents").value(1375));
    }

    @Test
    void legacyReturnRequestsAreConvertedToo() {
        ObjectId id = new ObjectId();
        mongo.getCollection("return_requests").insertOne(new Document("_id", id).append("orderId", "o-x")
                .append("status", "PICKED_UP").append("totalAmount", 27.5)
                .append("items", List.of(new Document("productId", "p-1").append("quantity", 2).append("price", 13.75))));
        backfill.backfill();
        Document doc = raw("return_requests", id);
        assertThat(doc).containsEntry("totalCents", 2750L).doesNotContainKey("totalAmount");
        assertThat(doc.getList("items", Document.class).get(0)).containsEntry("priceCents", 1375L).doesNotContainKey("price");
    }

    @Test
    void runningItAgainChangesNothingAndNewOrdersAreLeftAlone() {
        ObjectId legacy = legacyOrder("b-" + UUID.randomUUID());
        backfill.backfill();
        Document once = raw("orders", legacy);
        assertThat(backfill.backfill()).isZero();
        assertThat(raw("orders", legacy)).isEqualTo(once);
    }
}
