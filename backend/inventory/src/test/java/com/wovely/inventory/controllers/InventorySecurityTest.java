package com.wovely.inventory.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.wovely.common.error.ApiException;
import com.wovely.inventory.models.InventoryItem;
import com.wovely.inventory.repository.InventoryItemRepository;
import com.wovely.inventory.repository.InventoryRepository;
import com.wovely.inventory.services.InventoryService;
import com.wovely.inventory.stock.StockLedger;
import com.wovely.inventory.support.SecuredWebMvcTest;
import com.wovely.inventory.support.TestTokens;

/** PLAN.md 2.1 (roles), 1.3 (internal only) and 4.2 (inventory is the single stock writer). */
@WebMvcTest({ InventoryController.class, InternalStockController.class })
@Import(InventoryService.class)
class InventorySecurityTest extends SecuredWebMvcTest {

    @MockitoBean InventoryItemRepository items;
    @MockitoBean InventoryRepository inventories;
    @MockitoBean StockLedger ledger;

    InventoryItem item;

    @BeforeEach
    void stubs() {
        item = new InventoryItem();
        item.setProductId("p-1");
        item.setSellerId(TestTokens.SELLER_ID);
        item.setStockQuantity(3);
        item.setVersion(7L);
        item.setLowStockThreshold(5);
        when(items.findBySellerIdAndIsActive(TestTokens.SELLER_ID, true)).thenReturn(List.of(item));
        when(items.findBySellerIdAndProductId(TestTokens.SELLER_ID, "p-1")).thenReturn(Optional.of(item));
    }

    // ---------------------------------------------------------------- seller API (JWT)

    @Test
    void sellerReadsOwnInventory() throws Exception {
        mvc.perform(get("/api/inventory/seller/" + TestTokens.SELLER_ID).header(AUTH, bearer(TestTokens.seller())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void otherSellerAndBuyerAreForbiddenAdminAllowed() throws Exception {
        String url = "/api/inventory/seller/" + TestTokens.SELLER_ID;
        mvc.perform(get(url).header(AUTH, bearer(TestTokens.otherSeller()))).andExpect(status().isForbidden());
        mvc.perform(get(url).header(AUTH, bearer(TestTokens.buyer()))).andExpect(status().isForbidden());
        mvc.perform(get(url).header(AUTH, bearer(TestTokens.admin()))).andExpect(status().isOk());
    }

    @Test
    void sellerStockChangesGoThroughTheLedger() throws Exception {
        when(ledger.restock("p-1", TestTokens.SELLER_ID, 2)).thenReturn(item);
        mvc.perform(post("/api/inventory/seller/" + TestTokens.SELLER_ID + "/product/p-1/restock")
                .header(AUTH, bearer(TestTokens.seller())).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
            .andExpect(status().isOk());
        when(ledger.setLevel("p-1", TestTokens.SELLER_ID, 9)).thenReturn(item);
        mvc.perform(put("/api/inventory/seller/" + TestTokens.SELLER_ID + "/product/p-1/stock")
                .header(AUTH, bearer(TestTokens.seller())).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":9}"))
            .andExpect(status().isOk());
        verify(ledger).restock("p-1", TestTokens.SELLER_ID, 2);
        verify(ledger).setLevel("p-1", TestTokens.SELLER_ID, 9);
    }

    @Test
    void otherSellerCannotChangeStockAndInvalidQuantitiesAre400() throws Exception {
        mvc.perform(post("/api/inventory/seller/" + TestTokens.SELLER_ID + "/product/p-1/restock")
                .header(AUTH, bearer(TestTokens.otherSeller())).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/inventory/seller/" + TestTokens.SELLER_ID + "/product/p-1/restock")
                .header(AUTH, bearer(TestTokens.seller())).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}"))
            .andExpect(status().isBadRequest());
        verify(ledger, never()).restock(anyString(), anyString(), anyInt());
    }

    @Test
    void listingsCannotBeCreatedOrDeletedHere() throws Exception {
        mvc.perform(post("/api/inventory/seller/" + TestTokens.SELLER_ID + "/product").header(AUTH, bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":\"x\",\"stockQuantity\":999}"))
            .andExpect(status().is4xxClientError());
        mvc.perform(delete("/api/inventory/seller/" + TestTokens.SELLER_ID + "/product/p-1").header(AUTH, bearer(TestTokens.seller())))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void sellerViewsReturnTheirShapes() throws Exception {
        String base = "/api/inventory/seller/" + TestTokens.SELLER_ID;
        String seller = bearer(TestTokens.seller());
        mvc.perform(get(base + "/low-stock").header(AUTH, seller)).andExpect(status().isOk())
            .andExpect(jsonPath("$.alertType").value("LOW_STOCK")).andExpect(jsonPath("$.items[0].productId").value("p-1"));
        mvc.perform(get(base + "/out-of-stock").header(AUTH, seller)).andExpect(status().isOk())
            .andExpect(jsonPath("$.alertType").value("OUT_OF_STOCK")).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get(base + "/category/Uncategorised").header(AUTH, seller)).andExpect(status().isOk())
            .andExpect(jsonPath("$.category").value("Uncategorised")).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get(base + "/dashboard").header(AUTH, seller)).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalProducts").value(1)).andExpect(jsonPath("$.lowStock").value(1))
            .andExpect(jsonPath("$.categoryBreakdown.Uncategorised").value(1));
        mvc.perform(get(base + "/product/p-1").header(AUTH, seller)).andExpect(status().isOk())
            .andExpect(jsonPath("$.stockQuantity").value(3)).andExpect(jsonPath("$.isLowStock").value(true));
    }

    @Test
    void unknownProductIs404WithTheErrorBody() throws Exception {
        mvc.perform(get("/api/inventory/seller/" + TestTokens.SELLER_ID + "/product/nope").header(AUTH, bearer(TestTokens.seller())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.path").value("/api/inventory/seller/" + TestTokens.SELLER_ID + "/product/nope"));
    }

    @Test
    void everySellerViewIsOwnerOnly() throws Exception {
        String base = "/api/inventory/seller/" + TestTokens.SELLER_ID;
        for (String path : List.of("/low-stock", "/out-of-stock", "/category/Mugs", "/dashboard", "/product/p-1")) {
            mvc.perform(get(base + path).header(AUTH, bearer(TestTokens.otherSeller()))).andExpect(status().isForbidden());
            mvc.perform(get(base + path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void missingStockQuantityIs400() throws Exception {
        mvc.perform(put("/api/inventory/seller/" + TestTokens.SELLER_ID + "/product/p-1/stock")
                .header(AUTH, bearer(TestTokens.seller())).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        verify(ledger, never()).setLevel(anyString(), anyString(), anyInt());
    }

    // ---------------------------------------------------------------- internal stock API

    static final String STOCK = "/internal/inventory/stock/p-1";

    @Test
    void internalApiNeedsTheServiceKey() throws Exception {
        String body = "{\"sellerId\":\"" + TestTokens.SELLER_ID + "\",\"quantity\":1}";
        mvc.perform(post(STOCK + "/reserve").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
        mvc.perform(post(STOCK + "/reserve").header(AUTH, bearer(TestTokens.admin()))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        verify(ledger, never()).reserve(anyString(), anyString(), anyInt());
    }

    @Test
    void reserveReturnsTheNewLevelAndVersion() throws Exception {
        when(ledger.reserve("p-1", TestTokens.SELLER_ID, 2)).thenReturn(item);
        mvc.perform(post(STOCK + "/reserve").header("X-Internal-Api-Key", INTERNAL_KEY)
                .contentType(MediaType.APPLICATION_JSON).content("{\"sellerId\":\"" + TestTokens.SELLER_ID + "\",\"quantity\":2}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stockQuantity").value(3))
            .andExpect(jsonPath("$.version").value(7));
    }

    @Test
    void insufficientStockIs409() throws Exception {
        when(ledger.reserve("p-1", TestTokens.SELLER_ID, 5)).thenThrow(ApiException.conflict("Insufficient stock for product p-1"));
        mvc.perform(post(STOCK + "/reserve").header("X-Internal-Api-Key", INTERNAL_KEY)
                .contentType(MediaType.APPLICATION_JSON).content("{\"sellerId\":\"" + TestTokens.SELLER_ID + "\",\"quantity\":5}"))
            .andExpect(status().isConflict());
    }

    @Test
    void initValidatesTheBody() throws Exception {
        mvc.perform(put(STOCK).header("X-Internal-Api-Key", INTERNAL_KEY)
                .contentType(MediaType.APPLICATION_JSON).content("{\"initialQuantity\":-1}"))
            .andExpect(status().isBadRequest());
        when(ledger.init(eq("p-1"), eq(TestTokens.SELLER_ID), eq(4), eq(2), any())).thenReturn(item);
        mvc.perform(put(STOCK).header("X-Internal-Api-Key", INTERNAL_KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sellerId\":\"" + TestTokens.SELLER_ID + "\",\"initialQuantity\":4,\"lowStockThreshold\":2,\"productName\":\"Mug\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void initPassesThePriceInCentsAndRejectsNegativeOnes() throws Exception {
        when(ledger.init(eq("p-1"), eq(TestTokens.SELLER_ID), eq(1), any(), any())).thenReturn(item);
        mvc.perform(put(STOCK).header("X-Internal-Api-Key", INTERNAL_KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sellerId\":\"" + TestTokens.SELLER_ID + "\",\"initialQuantity\":1,\"priceCents\":4250}"))
            .andExpect(status().isOk());
        var details = org.mockito.ArgumentCaptor.forClass(com.wovely.inventory.stock.StockLedger.Details.class);
        verify(ledger).init(eq("p-1"), eq(TestTokens.SELLER_ID), eq(1), any(), details.capture());
        org.assertj.core.api.Assertions.assertThat(details.getValue().priceCents()).isEqualTo(4250L);
        mvc.perform(put(STOCK).header("X-Internal-Api-Key", INTERNAL_KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sellerId\":\"" + TestTokens.SELLER_ID + "\",\"initialQuantity\":1,\"priceCents\":-5}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void internalWritesGoToTheLedger() throws Exception {
        String body = "{\"sellerId\":\"" + TestTokens.SELLER_ID + "\",\"quantity\":2}";
        when(ledger.release("p-1", TestTokens.SELLER_ID, 2)).thenReturn(item);
        when(ledger.restock("p-1", TestTokens.SELLER_ID, 2)).thenReturn(item);
        when(ledger.setLevel("p-1", TestTokens.SELLER_ID, 2)).thenReturn(item);
        when(ledger.deactivate("p-1", TestTokens.SELLER_ID)).thenReturn(item);
        when(ledger.find("p-1", TestTokens.SELLER_ID)).thenReturn(Optional.of(item));
        mvc.perform(post(STOCK + "/release").header(INTERNAL_HEADER, INTERNAL_KEY).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
        mvc.perform(post(STOCK + "/restock").header(INTERNAL_HEADER, INTERNAL_KEY).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
        mvc.perform(put(STOCK + "/level").header(INTERNAL_HEADER, INTERNAL_KEY).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
        mvc.perform(get(STOCK).param("sellerId", TestTokens.SELLER_ID).header(INTERNAL_HEADER, INTERNAL_KEY))
            .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        mvc.perform(delete(STOCK).param("sellerId", TestTokens.SELLER_ID).header(INTERNAL_HEADER, INTERNAL_KEY))
            .andExpect(status().isOk());
        verify(ledger).release("p-1", TestTokens.SELLER_ID, 2);
        verify(ledger).deactivate("p-1", TestTokens.SELLER_ID);
    }

    @Test
    void internalGetOfUnknownRecordIs404AndMissingSellerIs400() throws Exception {
        when(ledger.find("p-1", "nobody")).thenReturn(Optional.empty());
        mvc.perform(get(STOCK).param("sellerId", "nobody").header(INTERNAL_HEADER, INTERNAL_KEY))
            .andExpect(status().isNotFound());
        mvc.perform(get(STOCK).header(INTERNAL_HEADER, INTERNAL_KEY)).andExpect(status().isBadRequest());
    }
}
