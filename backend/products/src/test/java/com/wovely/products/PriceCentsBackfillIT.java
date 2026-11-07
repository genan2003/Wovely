package com.wovely.products;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.wovely.products.services.PriceCentsBackfill;
import com.wovely.products.support.AbstractMongoIT;

/** PLAN.md 7.5 against real MongoDB: legacy double prices become cents once, and the API shows both forms. */
class PriceCentsBackfillIT extends AbstractMongoIT {

    @Autowired MongoTemplate mongo;
    @Autowired PriceCentsBackfill backfill;
    @Autowired MockMvc mvc;

    private ObjectId legacyListing(Object price) {
        ObjectId id = new ObjectId();
        mongo.getCollection("products").insertOne(new Document("_id", id).append("name", "Legacy " + UUID.randomUUID())
                .append("price", price).append("sellerId", "s-legacy").append("status", "APPROVED")
                .append("stockQuantity", 1).append("deliveryEmission", "LOW"));
        return id;
    }

    private Document raw(ObjectId id) {
        return mongo.getCollection("products").find(new Document("_id", id)).first();
    }

    @Test
    void doublePricesBecomeCentsAndTheLegacyFieldIsRemoved() throws Exception {
        ObjectId basket = legacyListing(42.5);
        ObjectId tricky = legacyListing(1.005); // 1.00499999… in binary; the stored intent is 1.005 → 1.01
        ObjectId whole = legacyListing(15);

        assertThat(backfill.backfill()).isGreaterThanOrEqualTo(3);

        assertThat(raw(basket)).containsEntry("priceCents", 4250L).doesNotContainKey("price");
        assertThat(raw(tricky)).containsEntry("priceCents", 101L);
        assertThat(raw(whole)).containsEntry("priceCents", 1500L);

        mvc.perform(get("/api/products/" + basket.toHexString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priceCents").value(4250))
            .andExpect(jsonPath("$.price").value(42.5));
    }

    @Test
    void runningItAgainChangesNothing() {
        ObjectId id = legacyListing(9.99);
        backfill.backfill();
        Document once = raw(id);
        assertThat(backfill.backfill()).isZero();
        assertThat(raw(id)).isEqualTo(once);
    }

    @Test
    void priceIsNeverWrittenBackByTheApplication() throws Exception {
        ObjectId id = legacyListing(20.0);
        backfill.backfill();
        // a full read-modify-save through the entity must not reintroduce the decimal field
        var p = mongo.findById(id, com.wovely.products.models.Product.class);
        p.setName(p.getName() + " (edited)");
        mongo.save(p);
        assertThat(raw(id)).doesNotContainKey("price").containsEntry("priceCents", 2000L);
    }
}
