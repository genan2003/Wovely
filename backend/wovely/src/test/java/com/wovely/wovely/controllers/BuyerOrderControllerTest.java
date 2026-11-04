package com.wovely.wovely.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.wovely.wovely.clients.CatalogClient;
import com.wovely.wovely.clients.CatalogClient.ProductSnapshot;
import com.wovely.wovely.exception.ApiException;
import com.wovely.wovely.models.EOrderStatus;
import com.wovely.wovely.models.Order;
import com.wovely.wovely.repository.AuditLogRepository;
import com.wovely.wovely.repository.OrderRepository;
import com.wovely.wovely.services.EcoCarrierService;
import com.wovely.wovely.services.OrderService;
import com.wovely.wovely.support.Orders;
import com.wovely.wovely.support.SecuredWebMvcTest;
import com.wovely.wovely.support.TestTokens;

/** PLAN.md 1.4 (ownership) and 1.5 (server-side pricing) for buyer order endpoints. */
@WebMvcTest(BuyerOrderController.class)
@Import(OrderService.class)
class BuyerOrderControllerTest extends SecuredWebMvcTest {

    @MockitoBean OrderRepository orderRepository;
    @MockitoBean AuditLogRepository auditLogRepository;
    @MockitoBean CatalogClient catalogClient;
    @MockitoBean EcoCarrierService ecoCarrierService;

    static final ProductSnapshot BASKET = new ProductSnapshot("p-1", "Basket", 10.0, TestTokens.SELLER_ID, "img", "APPROVED", 5);
    static final ProductSnapshot MUG = new ProductSnapshot("p-2", "Mug", 7.5, TestTokens.SELLER_ID, "img2", "APPROVED", 5);

    @BeforeEach
    void stubs() {
        when(catalogClient.getProduct("p-1")).thenReturn(BASKET);
        when(catalogClient.getProduct("p-2")).thenReturn(MUG);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) o.setId("new-order");
            return o;
        });
    }

    // ---------------------------------------------------------------- create (1.5)

    /** Legacy clients still send price/total/status/buyerId — all must be ignored. */
    static final String TAMPERED = """
        {"buyerId":"buyer-2","sellerId":"seller-2","status":"DELIVERED","totalAmount":0.01,
         "shippingAddress":"Str. Test 1",
         "items":[{"productId":"p-1","quantity":2,"price":0.01},{"productId":"p-2","quantity":1,"price":0.01}]}""";

    @Test
    void createOrderUsesServerPricesBuyerFromTokenAndPendingStatus() throws Exception {
        mvc.perform(post("/api/orders").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(TAMPERED))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.totalAmount").value(27.5))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.buyerId").value(TestTokens.BUYER_ID))
            .andExpect(jsonPath("$.sellerId").value(TestTokens.SELLER_ID))
            .andExpect(jsonPath("$.sellerName").value("seller"))
            .andExpect(jsonPath("$.items[0].price").value(10.0));
        verify(catalogClient).reduceStock(TestTokens.SELLER_ID, "p-1", 2);
        verify(catalogClient).reduceStock(TestTokens.SELLER_ID, "p-2", 1);
    }

    @Test
    void createOrderWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(TAMPERED))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void insufficientStockIsConflictAndCompensatesEarlierLines() throws Exception {
        doThrow(ApiException.conflict("Insufficient stock for product p-2"))
            .when(catalogClient).reduceStock(TestTokens.SELLER_ID, "p-2", 1);
        mvc.perform(post("/api/orders").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(TAMPERED))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Insufficient stock for product p-2"));
        verify(catalogClient).restock(TestTokens.SELLER_ID, "p-1", 2);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void itemsFromDifferentSellersAreRejected() throws Exception {
        when(catalogClient.getProduct("p-2"))
            .thenReturn(new ProductSnapshot("p-2", "Mug", 7.5, TestTokens.OTHER_SELLER_ID, "", "APPROVED", 5));
        mvc.perform(post("/api/orders").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(TAMPERED))
            .andExpect(status().isBadRequest());
        verify(catalogClient, never()).reduceStock(anyString(), anyString(), anyInt());
    }

    @Test
    void unapprovedProductCannotBeOrdered() throws Exception {
        when(catalogClient.getProduct("p-1"))
            .thenReturn(new ProductSnapshot("p-1", "Basket", 10.0, TestTokens.SELLER_ID, "", "PENDING", 5));
        mvc.perform(post("/api/orders").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(TAMPERED))
            .andExpect(status().isBadRequest());
    }

    @Test
    void zeroQuantityIsBadRequest() throws Exception {
        mvc.perform(post("/api/orders").header(AUTH, bearer(TestTokens.buyer())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":\"p-1\",\"quantity\":0}]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void emptyOrderIsBadRequest() throws Exception {
        mvc.perform(post("/api/orders").header(AUTH, bearer(TestTokens.buyer())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void sellerCannotBuyOwnProduct() throws Exception {
        mvc.perform(post("/api/orders").header(AUTH, bearer(TestTokens.seller())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":\"p-1\",\"quantity\":1}]}"))
            .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- ownership (1.4)

    @Test
    void buyerCanReadOwnOrder() throws Exception {
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(Orders.order("o-1", TestTokens.BUYER_ID, TestTokens.SELLER_ID, EOrderStatus.PENDING)));
        mvc.perform(get("/api/orders/o-1").header(AUTH, bearer(TestTokens.buyer())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("o-1"));
    }

    @Test
    void sellerOfOrderCanReadIt() throws Exception {
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(Orders.order("o-1", TestTokens.BUYER_ID, TestTokens.SELLER_ID, EOrderStatus.PENDING)));
        mvc.perform(get("/api/orders/o-1").header(AUTH, bearer(TestTokens.seller()))).andExpect(status().isOk());
    }

    @Test
    void otherBuyerCannotReadOrder() throws Exception {
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(Orders.order("o-1", TestTokens.BUYER_ID, TestTokens.SELLER_ID, EOrderStatus.PENDING)));
        mvc.perform(get("/api/orders/o-1").header(AUTH, bearer(TestTokens.otherBuyer()))).andExpect(status().isForbidden());
    }

    @Test
    void missingOrderIsNotFound() throws Exception {
        mvc.perform(get("/api/orders/nope").header(AUTH, bearer(TestTokens.buyer()))).andExpect(status().isNotFound());
    }

    @Test
    void otherBuyerCannotCancelOrder() throws Exception {
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(Orders.order("o-1", TestTokens.BUYER_ID, TestTokens.SELLER_ID, EOrderStatus.PENDING)));
        mvc.perform(post("/api/orders/o-1/cancel").header(AUTH, bearer(TestTokens.otherBuyer()))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        verify(catalogClient, never()).restock(anyString(), anyString(), anyInt());
    }

    @Test
    void buyerCancelsOwnOrderAndStockIsRestored() throws Exception {
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(Orders.order("o-1", TestTokens.BUYER_ID, TestTokens.SELLER_ID, EOrderStatus.PENDING)));
        mvc.perform(post("/api/orders/o-1/cancel").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"changed my mind\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(catalogClient).restock(TestTokens.SELLER_ID, "p-1", 2);
    }

    @Test
    void otherBuyerCannotConfirmReceiptOrMarkReviewed() throws Exception {
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(Orders.order("o-1", TestTokens.BUYER_ID, TestTokens.SELLER_ID, EOrderStatus.SHIPPED)));
        mvc.perform(post("/api/orders/o-1/confirm-receipt").header(AUTH, bearer(TestTokens.otherBuyer())))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/o-1/mark-reviewed").header(AUTH, bearer(TestTokens.otherBuyer()))
                .contentType(MediaType.APPLICATION_JSON).content("[\"p-1\"]"))
            .andExpect(status().isForbidden());
    }

    @Test
    void buyerHistoryIsSelfOnly() throws Exception {
        mvc.perform(get("/api/orders/buyer/" + TestTokens.BUYER_ID).header(AUTH, bearer(TestTokens.buyer())))
            .andExpect(status().isOk());
        mvc.perform(get("/api/orders/buyer/" + TestTokens.BUYER_ID).header(AUTH, bearer(TestTokens.otherBuyer())))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/buyer/" + TestTokens.BUYER_ID).header(AUTH, bearer(TestTokens.admin())))
            .andExpect(status().isOk());
    }

    @Test
    void cancelledOrderIsSavedWithReason() throws Exception {
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(Orders.order("o-1", TestTokens.BUYER_ID, TestTokens.SELLER_ID, EOrderStatus.PENDING)));
        mvc.perform(post("/api/orders/o-1/cancel").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"too slow\"}"));
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().getCancellationReason()).isEqualTo("too slow");
        verify(catalogClient, never()).reduceStock(anyString(), eq("p-1"), anyInt());
    }
}
