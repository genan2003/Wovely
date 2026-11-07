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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.wovely.products.models.Product;
import com.wovely.products.repository.CategoryRepository;
import com.wovely.products.repository.ProductRepository;
import com.wovely.products.services.StockService;
import com.wovely.products.services.CatalogQueryService;
import com.wovely.products.clients.AuditClient;
import com.wovely.common.error.ApiException;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import com.wovely.products.support.SecuredWebMvcTest;
import com.wovely.products.support.TestTokens;

/** PLAN.md 1.2 (product creation), 1.4 (seller ownership), 4.2 (stock is owned by inventory). */
@WebMvcTest({ ProductController.class, AdminProductController.class, CategoryController.class })
@org.springframework.context.annotation.Import({ com.wovely.products.services.ListingService.class, com.wovely.products.services.SellerListingQueries.class })
class ProductSecurityTest extends SecuredWebMvcTest {

    @MockitoBean ProductRepository productRepository;
    @MockitoBean CategoryRepository categoryRepository;
    @MockitoBean StockService stockService;
    @MockitoBean CatalogQueryService catalogQuery;
    @MockitoBean AuditClient auditClient;

    Product basket;

    @BeforeEach
    void stubs() {
        basket = new Product("Basket", "d", 1000L, TestTokens.SELLER_ID, "img", "Home", "Home", List.of(), "Sibiu",
                "Sibiu", null, null, "Low", "Courier", true, 3, 1);
        basket.setId("p-1");
        basket.setStatus("APPROVED");
        when(productRepository.findBySellerIdAndId(TestTokens.SELLER_ID, "p-1")).thenReturn(Optional.of(basket));
        when(productRepository.findById("p-1")).thenReturn(Optional.of(basket));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(stockService.register(any(Product.class), anyInt())).thenAnswer(inv -> inv.getArgument(0));
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
        when(catalogQuery.search(any(), any(), any())).thenReturn(new CatalogQueryService.Page(List.of(basket), 1));
        mvc.perform(get("/api/products")).andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Total-Count", "1"))
            .andExpect(jsonPath("$[0].id").value("p-1"));
        mvc.perform(get("/api/products/p-1")).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- 1.3 / 4.2 stock lives in inventory

    @Test
    void productsNoLongerExposesStockMutationsToOtherServices() throws Exception {
        String url = "/internal/products/seller/" + TestTokens.SELLER_ID + "/product/p-1/reduce-stock";
        mvc.perform(post(url).header(INTERNAL_HEADER, INTERNAL_KEY).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/products/seller/{s}/product/p-1/reduce-stock", TestTokens.SELLER_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":3}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void creatingAListingRegistersItsStockWithInventory() throws Exception {
        mvc.perform(post("/api/products").header(AUTH, bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Mug\",\"price\":9,\"stockQuantity\":7}"))
            .andExpect(status().isCreated());
        verify(stockService).register(any(Product.class), eq(7));
    }

    @Test
    void listingIsRolledBackWhenInventoryIsDown() throws Exception {
        when(stockService.register(any(Product.class), anyInt()))
            .thenThrow(new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Inventory service unavailable"));
        mvc.perform(post("/api/products").header(AUTH, bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Mug\",\"price\":9}"))
            .andExpect(status().isServiceUnavailable());
        verify(productRepository).delete(any(Product.class));
    }

    @Test
    void sellerStockEditsAreForwardedToInventory() throws Exception {
        when(stockService.setLevel(TestTokens.SELLER_ID, "p-1", 9)).thenReturn(basket);
        mvc.perform(put("/api/products/seller/" + TestTokens.SELLER_ID + "/product/p-1/stock").header(AUTH, bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":9}"))
            .andExpect(status().isOk());
        verify(stockService).setLevel(TestTokens.SELLER_ID, "p-1", 9);
        verify(productRepository, never()).save(any());
    }

    @Test
    void detailEditWithStockForwardsTheLevelAndRefreshesInventory() throws Exception {
        when(stockService.setLevel(TestTokens.SELLER_ID, "p-1", 4)).thenReturn(basket);
        mvc.perform(put("/api/products/seller/" + TestTokens.SELLER_ID + "/product/p-1").header(AUTH, bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Basket\",\"price\":10,\"stockQuantity\":4}"))
            .andExpect(status().isOk());
        verify(stockService).afterListingSaved(any(Product.class));
        verify(stockService).setLevel(TestTokens.SELLER_ID, "p-1", 4);
    }

    @Test
    void inventoryConflictsAreNotHiddenBehind500() throws Exception {
        when(stockService.setLevel(TestTokens.SELLER_ID, "p-1", 4)).thenThrow(ApiException.badRequest("quantity must be zero or more"));
        mvc.perform(put("/api/products/seller/" + TestTokens.SELLER_ID + "/product/p-1/stock").header(AUTH, bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":4}"))
            .andExpect(status().isBadRequest());
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
        verify(stockService, never()).restock(anyString(), anyString(), any());
    }

    @Test
    void sellerRestocksOwnProduct() throws Exception {
        basket.setStockQuantity(5);
        when(stockService.restock(TestTokens.SELLER_ID, "p-1", 2)).thenReturn(basket);
        mvc.perform(post("/api/products/seller/" + TestTokens.SELLER_ID + "/product/p-1/restock")
                .header(AUTH, bearer(TestTokens.seller())).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stockQuantity").value(5));
    }

    // ---------------------------------------------------------------- 2.8 soft delete, 2.7 moderation audit

    @Test
    void deleteArchivesInsteadOfRemoving() throws Exception {
        mvc.perform(delete("/api/products/seller/" + TestTokens.SELLER_ID + "/product/p-1").header(AUTH, bearer(TestTokens.seller())))
            .andExpect(status().isOk());
        assertThat(basket.getStatus()).isEqualTo("ARCHIVED");
        verify(productRepository, never()).delete(any());
        verify(stockService).afterListingSaved(basket); // deactivates the stock record in inventory
    }

    @Test
    void sellerViewsHideArchivedListings() throws Exception {
        mvc.perform(get("/api/products/seller/" + TestTokens.SELLER_ID).header(AUTH, bearer(TestTokens.seller())))
            .andExpect(status().isOk());
        verify(productRepository).findBySellerIdAndStatusNot(TestTokens.SELLER_ID, "ARCHIVED");
    }

    @Test
    void moderationValidatesStatusAndIsAudited() throws Exception {
        mvc.perform(put("/api/products/admin/p-1/status").header(AUTH, bearer(TestTokens.admin()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BOGUS\"}"))
            .andExpect(status().isBadRequest());
        basket.setStatus("PENDING");
        mvc.perform(put("/api/products/admin/p-1/status").header(AUTH, bearer(TestTokens.admin()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REJECTED\",\"reason\":\"Not handmade\"}"))
            .andExpect(status().isOk());
        verify(auditClient).record(eq("PRODUCT_STATUS"), eq("p-1"), contains("PENDING -> REJECTED"));
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

    // ---------------------------------------------------------------- regression: partial update

    @Test
    void updatingDetailsWithoutStockFieldsKeepsStockAndThreshold() throws Exception {
        mvc.perform(put("/api/products/seller/" + TestTokens.SELLER_ID + "/product/p-1").header(AUTH, bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Basket v2\",\"price\":12.0}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stockQuantity").value(3))
            .andExpect(jsonPath("$.lowStockThreshold").value(1));
    }

    @Test
    void adminEditWithoutStockFieldsKeepsStock() throws Exception {
        mvc.perform(put("/api/products/admin/p-1").header(AUTH, bearer(TestTokens.admin()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Fixed title\",\"price\":10.0}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stockQuantity").value(3));
    }

    // ---------------------------------------------------------------- 3.5 delivery emission

    @Test
    void sellerCannotClaimZeroEmissionItIsDerived() throws Exception {
        mvc.perform(post("/api/products").header(AUTH, bearer(TestTokens.seller())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mug\",\"price\":9,\"shippingMethod\":\"Express\",\"co2EmissionScore\":\"High\","
                        + "\"deliveryEmission\":\"ZERO\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.deliveryEmission").value("STANDARD"))
            .andExpect(jsonPath("$.localPickup").value(false));
    }

    @Test
    void localPickupMakesAListingZeroEmission() throws Exception {
        mvc.perform(post("/api/products").header(AUTH, bearer(TestTokens.seller())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mug\",\"price\":9,\"shippingMethod\":\"Express\",\"localPickup\":true}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.deliveryEmission").value("ZERO"));
    }

    @Test
    void editingShippingRecomputesEmission() throws Exception {
        mvc.perform(put("/api/products/seller/" + TestTokens.SELLER_ID + "/product/p-1").header(AUTH, bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Basket\",\"price\":10,\"shippingMethod\":\"Bicycle courier\",\"co2EmissionScore\":\"High\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deliveryEmission").value("ZERO"));
        mvc.perform(put("/api/products/admin/p-1").header(AUTH, bearer(TestTokens.admin()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Basket\",\"price\":10,\"shippingMethod\":\"Express\",\"co2EmissionScore\":\"High\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deliveryEmission").value("STANDARD"));
    }

    @Test
    void catalogPassesEmissionFiltersAndRejectsUnknownLevels() throws Exception {
        when(catalogQuery.search(any(), any(), any())).thenReturn(new CatalogQueryService.Page(List.of(), 0));
        mvc.perform(get("/api/products").param("deliveryEmission", "ZERO").param("localPickup", "true"))
            .andExpect(status().isOk());
        ArgumentCaptor<CatalogQueryService.Filter> filter = ArgumentCaptor.forClass(CatalogQueryService.Filter.class);
        verify(catalogQuery).search(filter.capture(), any(), any());
        assertThat(filter.getValue().deliveryEmission()).isEqualTo(com.wovely.products.models.EDeliveryEmission.ZERO);
        assertThat(filter.getValue().localPickup()).isTrue();

        mvc.perform(get("/api/products").param("deliveryEmission", "NEGATIVE")).andExpect(status().isBadRequest());
    }
}
