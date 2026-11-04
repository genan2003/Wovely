package com.wovely.inventory.controllers;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.wovely.inventory.models.InventoryItem;
import com.wovely.inventory.services.InventoryService;
import com.wovely.inventory.support.SecuredWebMvcTest;
import com.wovely.inventory.support.TestTokens;

/** PLAN.md 2.1 (roles from JWT), 1.3 (internal endpoints) and 1.4 (seller ownership). */
@WebMvcTest({ InventoryController.class, InternalInventoryController.class })
class InventorySecurityTest extends SecuredWebMvcTest {

    @MockitoBean InventoryService inventoryService;

    InventoryItem item;

    @BeforeEach
    void stubs() {
        item = new InventoryItem();
        item.setProductId("p-1");
        item.setStockQuantity(3);
        when(inventoryService.getInventoryItems(TestTokens.SELLER_ID)).thenReturn(List.of(item));
        when(inventoryService.getInventoryItem(TestTokens.SELLER_ID, "p-1")).thenReturn(Optional.of(item));
    }

    @Test
    void sellerReadsOwnInventoryNowThatRolesAreHonoured() throws Exception {
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
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/api/inventory/seller/" + TestTokens.SELLER_ID)).andExpect(status().isUnauthorized());
    }

    @Test
    void publicSyncAndReduceStockAreGone() throws Exception {
        mvc.perform(put("/api/inventory/seller/" + TestTokens.SELLER_ID + "/product/p-1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"stockQuantity\":999}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/inventory/seller/" + TestTokens.SELLER_ID + "/product/p-1/reduce-stock")
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":3}"))
            .andExpect(status().isUnauthorized());
        verify(inventoryService, never()).reduceStock(anyString(), anyString(), anyInt());
    }

    @Test
    void internalReduceStockNeedsKeyAndChecksStock() throws Exception {
        String url = "/internal/inventory/seller/" + TestTokens.SELLER_ID + "/product/p-1/reduce-stock";
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post(url).header(AUTH, bearer(TestTokens.seller())).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
            .andExpect(status().isForbidden());
        mvc.perform(post(url).header(INTERNAL_HEADER, INTERNAL_KEY).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":5}"))
            .andExpect(status().isConflict());

        InventoryItem after = new InventoryItem();
        after.setProductId("p-1");
        after.setStockQuantity(2);
        when(inventoryService.reduceStock(TestTokens.SELLER_ID, "p-1", 1)).thenReturn(Optional.of(after));
        mvc.perform(post(url).header(INTERNAL_HEADER, INTERNAL_KEY).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stockQuantity").value(2));
    }
}
