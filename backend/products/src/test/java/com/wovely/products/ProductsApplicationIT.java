package com.wovely.products;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.wovely.products.support.AbstractMongoIT;
import com.wovely.products.support.TestTokens;

/** Smoke test for the products service test harness (Testcontainers Mongo + JWT helpers). */
class ProductsApplicationIT extends AbstractMongoIT {

    @Autowired
    MockMvc mvc;

    @Test
    void categoriesAreSeededAndPublic() throws Exception {
        mvc.perform(get("/api/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isNotEmpty());
    }

    @Test
    void catalogIsPublic() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isOk());
    }

    @Test
    void sellerTokenCanReadSellerInventory() throws Exception {
        mvc.perform(get("/api/products/seller/{sellerId}", TestTokens.SELLER_ID)
                .header("Authorization", TestTokens.bearer(TestTokens.seller())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void buyerTokenIsRejectedOnSellerEndpoints() throws Exception {
        mvc.perform(get("/api/products/seller/{sellerId}", TestTokens.SELLER_ID)
                .header("Authorization", TestTokens.bearer(TestTokens.buyer())))
            .andExpect(status().isForbidden());
    }

    /** PLAN.md 4.5 — the OpenAPI description is served without a token and documents real endpoints and auth. */
    @org.junit.jupiter.api.Test
    void openApiSpecIsPublished() throws Exception {
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openapi").value(org.hamcrest.Matchers.startsWith("3.")))
            .andExpect(jsonPath("$.paths['/api/products']").exists())
            .andExpect(jsonPath("$.paths['/api/reviews']").exists())
            .andExpect(jsonPath("$.components.securitySchemes.bearerJwt.scheme").value("bearer"))
            .andExpect(jsonPath("$.servers[0].url").value("http://localhost:8090"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
