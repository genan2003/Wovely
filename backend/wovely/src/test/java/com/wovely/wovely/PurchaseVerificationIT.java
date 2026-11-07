package com.wovely.wovely;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.wovely.wovely.models.EOrderStatus;
import com.wovely.wovely.models.Order;
import com.wovely.wovely.models.OrderItem;
import com.wovely.wovely.repository.OrderRepository;
import com.wovely.wovely.services.PurchaseVerificationService;
import com.wovely.wovely.support.AbstractMongoIT;
import com.wovely.wovely.support.Orders;

/** PLAN.md 3.3 — the nested {@code items.productId} query against real MongoDB. */
class PurchaseVerificationIT extends AbstractMongoIT {

    @Autowired PurchaseVerificationService service;
    @Autowired OrderRepository orders;
    @Autowired MockMvc mvc;

    private final String buyer = "b-" + UUID.randomUUID();
    private final String seller = "s-" + UUID.randomUUID();

    private void order(EOrderStatus status, String... productIds) {
        Order o = Orders.order(null, buyer, seller, status);
        o.setOrderNumber("ORD-" + UUID.randomUUID());
        OrderItem[] items = new OrderItem[productIds.length];
        for (int i = 0; i < productIds.length; i++) {
            items[i] = new OrderItem(productIds[i], "Item", 1, 500L, "img");
        }
        o.setItems(items);
        orders.save(o);
    }

    @Test
    void productIsVerifiedOnlyOnceReceived() {
        order(EOrderStatus.SHIPPED, "p-shipped");
        order(EOrderStatus.CANCELLED, "p-cancelled");
        order(EOrderStatus.DELIVERED, "p-other", "p-delivered");
        order(EOrderStatus.COMPLETED, "p-completed");

        assertThat(service.hasReceivedProduct(buyer, "p-delivered")).isTrue();
        assertThat(service.hasReceivedProduct(buyer, "p-completed")).isTrue();
        assertThat(service.hasReceivedProduct(buyer, "p-shipped")).isFalse();
        assertThat(service.hasReceivedProduct(buyer, "p-cancelled")).isFalse();
        assertThat(service.hasReceivedProduct("someone-else", "p-delivered")).isFalse();
    }

    @Test
    void sellerIsVerifiedOnlyOnceAnOrderArrived() {
        order(EOrderStatus.PENDING, "x");
        assertThat(service.hasReceivedFromSeller(buyer, seller)).isFalse();
        order(EOrderStatus.DELIVERED, "y");
        assertThat(service.hasReceivedFromSeller(buyer, seller)).isTrue();
        assertThat(service.hasReceivedFromSeller(buyer, "other-seller")).isFalse();
    }

    @Test
    void internalEndpointAnswersWithServiceKey() throws Exception {
        order(EOrderStatus.COMPLETED, "p-api");
        mvc.perform(get("/internal/orders/verify-purchase").param("buyerId", buyer).param("productId", "p-api")
                .header("X-Internal-Api-Key", "test-internal-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verified").value(true));
    }
}
