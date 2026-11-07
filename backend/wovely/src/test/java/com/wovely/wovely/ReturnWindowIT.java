package com.wovely.wovely;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.wovely.wovely.models.EOrderStatus;
import com.wovely.wovely.models.Order;
import com.wovely.wovely.repository.OrderRepository;
import com.wovely.wovely.repository.UserRepository;
import com.wovely.wovely.support.AbstractMongoIT;
import com.wovely.wovely.support.Orders;
import com.wovely.wovely.support.TestTokens;

/** The 30-day return window is enforced by the API, not only hidden in the SPA. */
class ReturnWindowIT extends AbstractMongoIT {

    @Autowired MockMvc mvc;
    @Autowired OrderRepository orders;
    @Autowired UserRepository users;

    private Order delivered(Duration ago) {
        String buyerId = users.findByUsername("testuser").orElseThrow().getId(); // dev seed, matches TestTokens.buyer()
        Order o = Orders.order(null, buyerId, "s-" + UUID.randomUUID(), EOrderStatus.DELIVERED);
        o.setOrderNumber("ORD-" + UUID.randomUUID());
        o.setCreatedAt(Date.from(Instant.now().minus(Duration.ofDays(90))));
        o.setDeliveredAt(Date.from(Instant.now().minus(ago)));
        return orders.save(o);
    }

    private String returnOf(Order o) {
        return "{\"orderId\":\"" + o.getId() + "\",\"reason\":\"Too small\",\"items\":[{\"productId\":\"p-1\",\"quantity\":1}]}";
    }

    @Test
    void returnInsideTheWindowIsAccepted() throws Exception {
        Order o = delivered(Duration.ofDays(10));
        mvc.perform(post("/api/returns").header("Authorization", TestTokens.bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(returnOf(o)))
            .andExpect(status().is2xxSuccessful());
    }

    @Test
    void orderShowsItsReturnDeadline() throws Exception {
        Order o = delivered(Duration.ofDays(10));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/orders/" + o.getId())
                .header("Authorization", TestTokens.bearer(TestTokens.buyer())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deliveredAt").exists())
            .andExpect(jsonPath("$.returnDeadline").exists());
    }

    @Test
    void returnAfterTheWindowIs409() throws Exception {
        Order o = delivered(Duration.ofDays(31));
        mvc.perform(post("/api/returns").header("Authorization", TestTokens.bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(returnOf(o)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("30-day return window")));
    }
}
