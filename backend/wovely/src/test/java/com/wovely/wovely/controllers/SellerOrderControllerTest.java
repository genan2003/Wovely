package com.wovely.wovely.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.wovely.wovely.clients.CatalogClient;
import com.wovely.wovely.models.EOrderStatus;
import com.wovely.wovely.models.Order;
import com.wovely.wovely.repository.AuditLogRepository;
import com.wovely.wovely.repository.OrderRepository;
import com.wovely.wovely.services.EcoCarrierService;
import com.wovely.wovely.services.AuditService;
import com.wovely.wovely.services.OrderService;
import com.wovely.wovely.support.Orders;
import com.wovely.wovely.support.SecuredWebMvcTest;
import com.wovely.wovely.support.TestTokens;

/** PLAN.md 1.4 — sellers may only manage their own orders. */
@WebMvcTest(SellerOrderController.class)
@Import({ OrderService.class, AuditService.class, com.wovely.wovely.services.AddressService.class })
class SellerOrderControllerTest extends SecuredWebMvcTest {

    @MockitoBean OrderRepository orderRepository;
    @MockitoBean AuditLogRepository auditLogRepository;
    @MockitoBean CatalogClient catalogClient;
    @MockitoBean com.wovely.wovely.clients.InventoryClient inventoryClient;
    @MockitoBean com.wovely.wovely.services.WalletService walletService;
    @MockitoBean EcoCarrierService ecoCarrierService;

    @BeforeEach
    void stubs() {
        when(orderRepository.findById("o-1")).thenReturn(
            Optional.of(Orders.order("o-1", TestTokens.BUYER_ID, TestTokens.SELLER_ID, EOrderStatus.PENDING)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void sellerUpdatesOwnOrderStatus() throws Exception {
        mvc.perform(patch("/api/seller/orders/o-1/status").header(AUTH, bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PROCESSING\",\"estimatedDeliveryDays\":\"3\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.order.status").value("PROCESSING"))
            .andExpect(jsonPath("$.order.estimatedDeliveryDays").value(3));
    }

    @Test
    void otherSellerCannotUpdateStatus() throws Exception {
        mvc.perform(patch("/api/seller/orders/o-1/status").header(AUTH, bearer(TestTokens.otherSeller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SHIPPED\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void buyerCannotUseSellerEndpoints() throws Exception {
        mvc.perform(patch("/api/seller/orders/o-1/status").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELIVERED\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void invalidStatusIsBadRequest() throws Exception {
        mvc.perform(patch("/api/seller/orders/o-1/status").header(AUTH, bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"TELEPORTED\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void otherSellerCannotEditOrGenerateLabel() throws Exception {
        mvc.perform(put("/api/seller/orders/o-1/edit").header(AUTH, bearer(TestTokens.otherSeller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"shippingAddress\":\"x\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/seller/orders/o-1/eco-label").header(AUTH, bearer(TestTokens.otherSeller())))
            .andExpect(status().isForbidden());
    }

    @Test
    void editingLockedOrderIsConflict() throws Exception {
        when(orderRepository.findById("o-1")).thenReturn(
            Optional.of(Orders.order("o-1", TestTokens.BUYER_ID, TestTokens.SELLER_ID, EOrderStatus.PROCESSING)));
        mvc.perform(put("/api/seller/orders/o-1/edit").header(AUTH, bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"shippingAddress\":\"late\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("locked")));
    }

    @Test
    void sellerListsOnlyOwnOrders() throws Exception {
        mvc.perform(get("/api/seller/orders/seller/" + TestTokens.SELLER_ID).header(AUTH, bearer(TestTokens.seller())))
            .andExpect(status().isOk());
        mvc.perform(get("/api/seller/orders/seller/" + TestTokens.SELLER_ID).header(AUTH, bearer(TestTokens.otherSeller())))
            .andExpect(status().isForbidden());
    }

    @Test
    void manualOrderForAnotherSellerIsForbidden() throws Exception {
        String body = """
            {"sellerId":"%s","sellerName":"seller","buyerName":"Fair visitor","shippingAddress":"Stand 4",
             "items":[{"productId":"p-1","productName":"Basket","quantity":1,"price":10.0}]}""";
        mvc.perform(post("/api/seller/orders/manual").header(AUTH, bearer(TestTokens.otherSeller()))
                .contentType(MediaType.APPLICATION_JSON).content(body.formatted(TestTokens.SELLER_ID)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/seller/orders/manual").header(AUTH, bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content(body.formatted(TestTokens.SELLER_ID)))
            .andExpect(status().isCreated());
    }

    // ---------------------------------------------------------------- 3.6 ship with platform

    @Test
    void shipWithPlatformLabelThenShip() throws Exception {
        Order processing = Orders.order("o-1", TestTokens.BUYER_ID, TestTokens.SELLER_ID, EOrderStatus.PROCESSING);
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(processing));
        when(ecoCarrierService.generateLabel(any(), any())).thenReturn(new EcoCarrierService.ShippingLabel(
            new EcoCarrierService.EcoCarrier("GRL", "GreenLogistics", "Bicycle & Electric Van", 0.05, 40),
            "WVL-GRL-2345678ABC", 12, 0.6, "--- LABEL ---"));

        mvc.perform(patch("/api/seller/orders/o-1/status").header(AUTH, bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SHIPPED\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("shipping label")));

        mvc.perform(post("/api/seller/orders/o-1/eco-label").header(AUTH, bearer(TestTokens.seller())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.label").value("--- LABEL ---"))
            .andExpect(jsonPath("$.order.trackingNumber").value("WVL-GRL-2345678ABC"))
            .andExpect(jsonPath("$.order.carrierName").value("GreenLogistics"))
            .andExpect(jsonPath("$.order.estimatedCo2Kg").value(0.6));

        mvc.perform(post("/api/seller/orders/o-1/eco-label").header(AUTH, bearer(TestTokens.seller())))
            .andExpect(status().isConflict());

        mvc.perform(patch("/api/seller/orders/o-1/status").header(AUTH, bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SHIPPED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.order.status").value("SHIPPED"));
    }

    @Test
    void labelForPendingOrderIsConflictAndMissingOrderIs404() throws Exception {
        mvc.perform(post("/api/seller/orders/o-1/eco-label").header(AUTH, bearer(TestTokens.seller())))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/seller/orders/missing/eco-label").header(AUTH, bearer(TestTokens.seller())))
            .andExpect(status().isNotFound());
    }
}
