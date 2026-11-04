package com.wovely.inventory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.wovely.inventory.support.AbstractMongoIT;
import com.wovely.inventory.support.TestTokens;

/** Smoke test for the inventory service test harness (Testcontainers Mongo + JWT helpers). */
class InventoryApplicationIT extends AbstractMongoIT {

    @Autowired
    MockMvc mvc;

    @Test
    void sellerEndpointWithoutTokenIsRejected() throws Exception {
        mvc.perform(get("/api/inventory/seller/{sellerId}", "any-seller"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void sellerTokenCanReadOwnInventory() throws Exception {
        mvc.perform(get("/api/inventory/seller/{sellerId}", TestTokens.SELLER_ID)
                .header("Authorization", TestTokens.bearer(TestTokens.seller())))
            .andExpect(status().isOk());
    }
}
