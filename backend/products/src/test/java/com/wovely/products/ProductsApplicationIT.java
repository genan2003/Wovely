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
}
