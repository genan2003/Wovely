package com.wovely.products;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.wovely.products.clients.AuditClient;
import com.wovely.products.clients.InventoryClient;
import com.wovely.products.models.Product;
import com.wovely.products.repository.ProductRepository;
import com.wovely.products.support.AbstractMongoIT;
import com.wovely.products.support.TestTokens;

/**
 * A seller edit of an approved listing takes it out of the catalog and back into the admin queue,
 * without wiping fields the request didn't carry (docs/diagrams/state/product-status.puml).
 */
class ListingReviewIT extends AbstractMongoIT {

    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @MockitoBean InventoryClient inventory; // stock is not under test here
    @MockitoBean AuditClient audit;

    Product approvedListing() {
        Product p = new Product();
        p.setName("Mug " + UUID.randomUUID());
        p.setPriceCents(8000L);
        p.setSellerId(TestTokens.SELLER_ID);
        p.setMaterials(List.of("clay", "glaze"));
        p.setLatitude(45.79);
        p.setLongitude(24.15);
        p.setStockQuantity(4);
        p.setStatus("APPROVED");
        p.refreshDeliveryEmission();
        return products.save(p);
    }

    @Test
    void contentEditResubmitsTheListing() throws Exception {
        Product p = approvedListing();
        String seller = TestTokens.bearer(TestTokens.seller());
        String admin = TestTokens.bearer(TestTokens.admin());

        mvc.perform(get("/api/products")).andExpect(jsonPath("$[*].id", hasItem(p.getId())));

        mvc.perform(put("/api/products/seller/{s}/product/{id}", TestTokens.SELLER_ID, p.getId()).header("Authorization", seller)
                .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Now with a speckled glaze\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"));

        mvc.perform(get("/api/products")).andExpect(jsonPath("$[*].id", not(hasItem(p.getId()))));
        mvc.perform(get("/api/products/admin/pending").header("Authorization", admin))
            .andExpect(jsonPath("$[*].id", hasItem(p.getId())));

        Product stored = products.findById(p.getId()).orElseThrow();
        assertThat(stored.getDescription()).isEqualTo("Now with a speckled glaze");
        assertThat(stored.getMaterials()).containsExactly("clay", "glaze");
        assertThat(stored.getLatitude()).isEqualTo(45.79);
        assertThat(stored.getStockQuantity()).isEqualTo(4);

        mvc.perform(put("/api/products/admin/{id}/status", p.getId()).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/products")).andExpect(jsonPath("$[*].id", hasItem(p.getId())));
    }

    @Test
    void priceEditStaysLive() throws Exception {
        Product p = approvedListing();
        mvc.perform(put("/api/products/seller/{s}/product/{id}", TestTokens.SELLER_ID, p.getId())
                .header("Authorization", TestTokens.bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"price\":72.5}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.price").value(72.5));
        assertThat(products.findById(p.getId()).orElseThrow().getStatus()).isEqualTo("APPROVED");
    }
}
