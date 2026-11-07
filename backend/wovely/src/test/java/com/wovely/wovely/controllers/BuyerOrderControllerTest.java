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
import com.wovely.common.error.ApiException;
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

/** PLAN.md 1.4 (ownership) and 1.5 (server-side pricing) for buyer order endpoints. */
@WebMvcTest({ BuyerOrderController.class, DeliveryOptionController.class })
@Import({ OrderService.class, AuditService.class, com.wovely.wovely.services.AddressService.class })
class BuyerOrderControllerTest extends SecuredWebMvcTest {

    @MockitoBean OrderRepository orderRepository;
    @MockitoBean AuditLogRepository auditLogRepository;
    @MockitoBean CatalogClient catalogClient;
    @MockitoBean com.wovely.wovely.clients.InventoryClient inventoryClient;
    @MockitoBean com.wovely.wovely.services.WalletService walletService;
    @MockitoBean EcoCarrierService ecoCarrierService;

    static final ProductSnapshot BASKET = new ProductSnapshot("p-1", "Basket", 1000L, TestTokens.SELLER_ID, "img", "APPROVED", 5);
    static final ProductSnapshot MUG = new ProductSnapshot("p-2", "Mug", 750L, TestTokens.SELLER_ID, "img2", "APPROVED", 5);

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
        {"deliveryOption":"BICYCLE","buyerId":"buyer-2","sellerId":"seller-2","status":"DELIVERED","totalAmount":0.01,
         "shippingAddress":"Str. Test 1",
         "items":[{"productId":"p-1","quantity":2,"price":0.01},{"productId":"p-2","quantity":1,"price":0.01}]}""";

    @Test
    void createOrderUsesServerPricesBuyerFromTokenAndPendingStatus() throws Exception {
        mvc.perform(post("/api/orders").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(TAMPERED))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.totalAmount").value(37.5)) // 27.50 items + 10.00 bicycle delivery
            .andExpect(jsonPath("$.deliveryOption").value("BICYCLE"))
            .andExpect(jsonPath("$.deliveryFee").value(10.0))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.buyerId").value(TestTokens.BUYER_ID))
            .andExpect(jsonPath("$.sellerId").value(TestTokens.SELLER_ID))
            .andExpect(jsonPath("$.sellerName").value("seller"))
            .andExpect(jsonPath("$.items[0].price").value(10.0))
            .andExpect(jsonPath("$.totalCents").value(3750)) // PLAN 7.5: exact cents next to the decimals
            .andExpect(jsonPath("$.deliveryFeeCents").value(1000))
            .andExpect(jsonPath("$.items[1].priceCents").value(750));
        verify(inventoryClient).reserve(TestTokens.SELLER_ID, "p-1", 2);
        verify(inventoryClient).reserve(TestTokens.SELLER_ID, "p-2", 1);
    }

    @Test
    void createOrderWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(TAMPERED))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void insufficientStockIsConflictAndCompensatesEarlierLines() throws Exception {
        doThrow(ApiException.conflict("Insufficient stock for product p-2"))
            .when(inventoryClient).reserve(TestTokens.SELLER_ID, "p-2", 1);
        mvc.perform(post("/api/orders").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(TAMPERED))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Insufficient stock for product p-2"));
        verify(inventoryClient).release(TestTokens.SELLER_ID, "p-1", 2);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void itemsFromDifferentSellersAreRejected() throws Exception {
        when(catalogClient.getProduct("p-2"))
            .thenReturn(new ProductSnapshot("p-2", "Mug", 750L, TestTokens.OTHER_SELLER_ID, "", "APPROVED", 5));
        mvc.perform(post("/api/orders").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(TAMPERED))
            .andExpect(status().isBadRequest());
        verify(inventoryClient, never()).reserve(anyString(), anyString(), anyInt());
    }

    @Test
    void unapprovedProductCannotBeOrdered() throws Exception {
        when(catalogClient.getProduct("p-1"))
            .thenReturn(new ProductSnapshot("p-1", "Basket", 1000L, TestTokens.SELLER_ID, "", "PENDING", 5));
        mvc.perform(post("/api/orders").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(TAMPERED))
            .andExpect(status().isBadRequest());
    }

    @Test
    void zeroQuantityIsBadRequest() throws Exception {
        mvc.perform(post("/api/orders").header(AUTH, bearer(TestTokens.buyer())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"deliveryOption\":\"BICYCLE\",\"items\":[{\"productId\":\"p-1\",\"quantity\":0}]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void emptyOrderIsBadRequest() throws Exception {
        mvc.perform(post("/api/orders").header(AUTH, bearer(TestTokens.buyer())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"deliveryOption\":\"BICYCLE\",\"items\":[]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void sellerCannotBuyOwnProduct() throws Exception {
        mvc.perform(post("/api/orders").header(AUTH, bearer(TestTokens.seller())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"deliveryOption\":\"BICYCLE\",\"items\":[{\"productId\":\"p-1\",\"quantity\":1}]}"))
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
        verify(inventoryClient, never()).release(anyString(), anyString(), anyInt());
    }

    @Test
    void buyerCancelsOwnOrderAndStockIsRestored() throws Exception {
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(Orders.order("o-1", TestTokens.BUYER_ID, TestTokens.SELLER_ID, EOrderStatus.PENDING)));
        mvc.perform(post("/api/orders/o-1/cancel").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"changed my mind\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(inventoryClient).release(TestTokens.SELLER_ID, "p-1", 2);
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
        verify(inventoryClient, never()).reserve(anyString(), eq("p-1"), anyInt());
    }

    // ---------------------------------------------------------------- status timeline (PLAN 7.1)

    @Test
    void checkoutStartsTheTimelineWithTheBuyer() throws Exception {
        mvc.perform(post("/api/orders").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(TAMPERED))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.timeline.length()").value(1))
            .andExpect(jsonPath("$.timeline[0].status").value("PENDING"))
            .andExpect(jsonPath("$.timeline[0].from").doesNotExist())
            .andExpect(jsonPath("$.timeline[0].actor").value("BUYER"))
            .andExpect(jsonPath("$.timeline[0].inferred").value(false))
            .andExpect(jsonPath("$.timeline[0].actorId").doesNotExist());
    }

    @Test
    void cancellingAppendsATimelineEntryWithTheReason() throws Exception {
        Order o = Orders.order("o-1", TestTokens.BUYER_ID, TestTokens.SELLER_ID, EOrderStatus.PENDING);
        o.recordStatusChange(new com.wovely.wovely.models.OrderStatusChange(null, EOrderStatus.PENDING,
                o.getCreatedAt(), "BUYER", TestTokens.BUYER_ID, null));
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(o));
        mvc.perform(post("/api/orders/o-1/cancel").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"changed my mind\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timeline.length()").value(2))
            .andExpect(jsonPath("$.timeline[1].from").value("PENDING"))
            .andExpect(jsonPath("$.timeline[1].status").value("CANCELLED"))
            .andExpect(jsonPath("$.timeline[1].actor").value("BUYER"))
            .andExpect(jsonPath("$.timeline[1].reason").value("changed my mind"));
    }

    @Test
    void legacyOrderShowsAnInferredTimeline() throws Exception {
        Order o = Orders.order("o-1", TestTokens.BUYER_ID, TestTokens.SELLER_ID, EOrderStatus.SHIPPED);
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(o));
        mvc.perform(get("/api/orders/o-1").header(AUTH, bearer(TestTokens.buyer())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timeline[*].status").value(org.hamcrest.Matchers.contains("PENDING", "SHIPPED")))
            .andExpect(jsonPath("$.timeline[*].inferred").value(org.hamcrest.Matchers.contains(true, true)));
        mvc.perform(get("/api/orders/o-1").header(AUTH, bearer(TestTokens.otherBuyer())))
            .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- checkout address (PLAN 3, address book)

    static final String ITEMS_ONLY = """
        {"deliveryOption":"BICYCLE","items":[{"productId":"p-1","quantity":1}]}""";

    private com.wovely.wovely.models.Address saveAddress(String userKey, String id, boolean isDefault) {
        var a = new com.wovely.wovely.models.Address();
        a.setId(id);
        a.setRecipientName("Ana Pop");
        a.setStreet("Str. Florilor 3");
        a.setPostalCode("550000");
        a.setCity("Sibiu");
        a.setCountry("Romania");
        a.setDefault(isDefault);
        users.get(userKey).getAddresses().add(a);
        return a;
    }

    @Test
    void checkoutWithAddressIdUsesTheFormattedBookEntry() throws Exception {
        saveAddress("testuser", "home", false);
        mvc.perform(post("/api/orders").header(AUTH, bearer(TestTokens.buyer())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"deliveryOption\":\"BICYCLE\",\"addressId\":\"home\",\"shippingAddress\":\"ignored\",\"items\":[{\"productId\":\"p-1\",\"quantity\":1}]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.shippingAddress").value("Ana Pop, Str. Florilor 3, 550000 Sibiu, Romania"));
    }

    @Test
    void checkoutWithoutAddressUsesTheDefault() throws Exception {
        saveAddress("testuser", "work", false);
        saveAddress("testuser", "home", true);
        mvc.perform(post("/api/orders").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(ITEMS_ONLY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.shippingAddress").value(org.hamcrest.Matchers.startsWith("Ana Pop")));
    }

    @Test
    void someoneElsesAddressIs404AndNoStockIsTouched() throws Exception {
        saveAddress("buyer2", "theirs", true);
        mvc.perform(post("/api/orders").header(AUTH, bearer(TestTokens.buyer())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"deliveryOption\":\"BICYCLE\",\"addressId\":\"theirs\",\"items\":[{\"productId\":\"p-1\",\"quantity\":1}]}"))
            .andExpect(status().isNotFound());
        verify(inventoryClient, never()).reserve(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void noAddressAtAllIsBadRequestBeforeStock() throws Exception {
        mvc.perform(post("/api/orders").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(ITEMS_ONLY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("shipping address")));
        verify(inventoryClient, never()).reserve(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    // ---------------------------------------------------------------- 3.7 wallet at checkout

    @Test
    void checkoutWithoutADeliveryOptionIs400BeforeAnyStockChange() throws Exception {
        mvc.perform(post("/api/orders").header(AUTH, bearer(TestTokens.buyer())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":\"p-1\",\"quantity\":1}]}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders").header(AUTH, bearer(TestTokens.buyer())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"deliveryOption\":\"TELEPORT\",\"items\":[{\"productId\":\"p-1\",\"quantity\":1}]}"))
            .andExpect(status().isBadRequest());
        verify(inventoryClient, never()).reserve(any(), any(), anyInt());
        verify(walletService, never()).chargeOrder(any(Order.class));
    }

    @Test
    void theClientCannotSetTheDeliveryFee() throws Exception {
        mvc.perform(post("/api/orders").header(AUTH, bearer(TestTokens.buyer())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"deliveryOption\":\"CARBON_NEUTRAL_COURIER\",\"deliveryFee\":0,\"shippingAddress\":\"Str. Lunga 1, Sibiu\",\"items\":[{\"productId\":\"p-1\",\"quantity\":1}]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.deliveryFee").value(25.0))
            .andExpect(jsonPath("$.estimatedDeliveryDays").value(2));
    }

    @Test
    void deliveryPriceListIsPublic() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/public/delivery-options"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].id").value("BICYCLE"))
            .andExpect(jsonPath("$[0].fee").value(10.0))
            .andExpect(jsonPath("$[0].feeCents").value(1000));
    }

    @Test
    void checkoutChargesTheWallet() throws Exception {
        mvc.perform(post("/api/orders").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(TAMPERED))
            .andExpect(status().isCreated());
        verify(walletService).chargeOrder(any(Order.class));
    }

    @Test
    void insufficientBalanceIs409RestocksAndLeavesNoOrder() throws Exception {
        doThrow(ApiException.conflict("Insufficient wallet balance")).when(walletService).chargeOrder(any(Order.class));
        mvc.perform(post("/api/orders").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(TAMPERED))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Insufficient wallet balance"));
        verify(inventoryClient).release(TestTokens.SELLER_ID, "p-1", 2);
        verify(inventoryClient).release(TestTokens.SELLER_ID, "p-2", 1);
        verify(orderRepository).delete(any(Order.class));
    }
}
