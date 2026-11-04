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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.wovely.common.error.ApiException;
import com.wovely.inventory.models.InventoryItem;
import com.wovely.inventory.services.InventoryService;
import com.wovely.inventory.stock.StockLedger;
import com.wovely.inventory.support.SecuredWebMvcTest;
import com.wovely.inventory.support.TestTokens;

/** PLAN.md 2.1 (roles), 1.3 (internal only) and 4.2 (inventory is the single stock writer). */
@WebMvcTest({ InventoryController.class, InternalStockController.class })
class InventorySecurityTest extends SecuredWebMvcTest {

    @MockitoBean InventoryService inventoryService;
    @MockitoBean StockLedger ledger;

    InventoryItem item;

    @BeforeEach
    void stubs() {
        item = new InventoryItem();
        item.setProductId("p-1");
        item.setSellerId(TestTokens.SELLER_ID);
        item.setStockQuantity(3);
        item.setVersion(7L);
        when(inventoryService.getInventoryItems(TestTokens.SELLER_ID)).thenReturn(List.of(item));
        when(inventoryService.getInventoryItem(TestTokens.SELLER_ID, "p-1")).thenReturn(Optional.of(item));
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
}
