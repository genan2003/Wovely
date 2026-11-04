package com.wovely.products.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.wovely.products.clients.InventorySyncClient;
import com.wovely.products.models.Product;
import com.wovely.products.repository.CategoryRepository;
import com.wovely.products.repository.ProductRepository;
import com.wovely.products.services.StockService;
import com.wovely.products.support.SecuredWebMvcTest;
import com.wovely.products.support.TestTokens;

/** PLAN.md 1.2 (product creation), 1.3 (internal stock endpoints), 1.4 (seller ownership). */
@WebMvcTest({ ProductController.class, InternalProductController.class, AdminProductController.class, CategoryController.class })
@Import(StockService.class)
class ProductSecurityTest extends SecuredWebMvcTest {

    @MockitoBean ProductRepository productRepository;
    @MockitoBean CategoryRepository categoryRepository;
    @MockitoBean InventorySyncClient inventorySync;

    Product basket;

    @BeforeEach
    void stubs() {
        basket = new Product("Basket", "d", 10.0, TestTokens.SELLER_ID, "img", "Home", "Home", List.of(), "Sibiu",
                "Sibiu", null, null, "Low", "Courier", true, 3, 1);
        basket.setId("p-1");
        basket.setStatus("APPROVED");
        when(productRepository.findBySellerIdAndId(TestTokens.SELLER_ID, "p-1")).thenReturn(Optional.of(basket));
        when(productRepository.findById("p-1")).thenReturn(Optional.of(basket));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------------------------------------------------------------- 1.2 create

    static final String NEW_PRODUCT = """
        {"id":"p-1","name":"Evil overwrite","price":1.0,"sellerId":"seller-2","status":"APPROVED","stockQuantity":4}""";

    @Test
    void anonymousCannotCreateProduct() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(NEW_PRODUCT))
            .andExpect(status().isUnauthorized());
        verify(productRepository, never()).save(any());
    }

    @Test
    void buyerCannotCreateProduct() throws Exception {
        mvc.perform(post("/api/products").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(NEW_PRODUCT))
            .andExpect(status().isForbidden());
    }

    @Test
    void sellerCreatesPendingProductOwnedByThemWithFreshId() throws Exception {
        mvc.perform(post("/api/products").header(AUTH, bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content(NEW_PRODUCT))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.sellerId").value(TestTokens.SELLER_ID));
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).as("client id must be ignored").isNull();
    }

    @Test
    void productWithoutNameIsBadRequest() throws Exception {
        mvc.perform(post("/api/products").header(AUTH, bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"price\":5}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void catalogStaysPublic() throws Exception {
        when(productRepository.findByStatus("APPROVED")).thenReturn(List.of(basket));
        mvc.perform(get("/api/products")).andExpect(status().isOk());
        mvc.perform(get("/api/products/p-1")).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- 1.3 internal stock

    @Test
    void oldPublicReduceStockPathNoLongerWorksAnonymously() throws Exception {
        mvc.perform(post("/api/products/seller/{s}/product/p-1/reduce-stock", TestTokens.SELLER_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":3}"))
            .andExpect(status().isUnauthorized());
        assertThat(basket.getStockQuantity()).isEqualTo(3);
    }

    @Test
    void internalStockEndpointRequiresApiKey() throws Exception {
        String url = "/internal/products/seller/" + TestTokens.SELLER_ID + "/product/p-1/reduce-stock";
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post(url).header(INTERNAL_HEADER, "wrong-key").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post(url).header(AUTH, bearer(TestTokens.admin())).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
            .andExpect(status().isForbidden());
        assertThat(basket.getStockQuantity()).isEqualTo(3);
    }

    @Test
    void internalReduceStockWithKeyDeductsAndConflictsWhenShort() throws Exception {
        String url = "/internal/products/seller/" + TestTokens.SELLER_ID + "/product/p-1/reduce-stock";
        mvc.perform(post(url).header(INTERNAL_HEADER, INTERNAL_KEY).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stockQuantity").value(1));
        mvc.perform(post(url).header(INTERNAL_HEADER, INTERNAL_KEY).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
            .andExpect(status().isConflict());
        mvc.perform(post(url).header(INTERNAL_HEADER, INTERNAL_KEY).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}"))
            .andExpect(status().isBadRequest());
        verify(inventorySync).sync(basket);
    }

    @Test
    void internalRestockForUnknownProductIsNotFound() throws Exception {
        mvc.perform(post("/internal/products/seller/" + TestTokens.SELLER_ID + "/product/nope/restock")
                .header(INTERNAL_HEADER, INTERNAL_KEY).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
            .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- 1.4 ownership

    @Test
    void sellerManagesOnlyOwnProducts() throws Exception {
        String own = "/api/products/seller/" + TestTokens.SELLER_ID;
        mvc.perform(get(own).header(AUTH, bearer(TestTokens.seller()))).andExpect(status().isOk());
        mvc.perform(get(own).header(AUTH, bearer(TestTokens.otherSeller()))).andExpect(status().isForbidden());
        mvc.perform(get(own).header(AUTH, bearer(TestTokens.admin()))).andExpect(status().isOk());
        mvc.perform(put(own + "/product/p-1/stock").header(AUTH, bearer(TestTokens.otherSeller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}"))
            .andExpect(status().isForbidden());
        mvc.perform(post(own + "/product/p-1/restock").header(AUTH, bearer(TestTokens.otherSeller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":100}"))
            .andExpect(status().isForbidden());
        assertThat(basket.getStockQuantity()).isEqualTo(3);
    }

    @Test
    void sellerRestocksOwnProduct() throws Exception {
        mvc.perform(post("/api/products/seller/" + TestTokens.SELLER_ID + "/product/p-1/restock")
                .header(AUTH, bearer(TestTokens.seller())).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stockQuantity").value(5));
    }

    @Test
    void onlyAdminsModerateProductsAndCategories() throws Exception {
        mvc.perform(put("/api/products/admin/p-1/status").header(AUTH, bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/products/admin/p-1/status").header(AUTH, bearer(TestTokens.admin()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/categories").header(AUTH, bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Spam\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/categories")).andExpect(status().isOk());
    }

    @Test
    void expiredTokenIsTreatedAsAnonymous() throws Exception {
        mvc.perform(get("/api/products/seller/" + TestTokens.SELLER_ID)
                .header(AUTH, bearer(TestTokens.expired("seller", TestTokens.SELLER_ID, "ROLE_SELLER"))))
            .andExpect(status().isUnauthorized());
    }
}
