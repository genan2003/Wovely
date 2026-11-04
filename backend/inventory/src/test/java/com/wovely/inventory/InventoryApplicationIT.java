package com.wovely.inventory;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    /** PLAN.md 4.5 — the OpenAPI description is served without a token and documents real endpoints and auth. */
    @org.junit.jupiter.api.Test
    void openApiSpecIsPublished() throws Exception {
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openapi").value(org.hamcrest.Matchers.startsWith("3.")))
            .andExpect(jsonPath("$.paths['/api/inventory/seller/{sellerId}']").exists())
            .andExpect(jsonPath("$.paths['/internal/inventory/stock/{productId}/reserve']").exists())
            .andExpect(jsonPath("$.components.securitySchemes.bearerJwt.scheme").value("bearer"))
            .andExpect(jsonPath("$.servers[0].url").value("http://localhost:8090"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
