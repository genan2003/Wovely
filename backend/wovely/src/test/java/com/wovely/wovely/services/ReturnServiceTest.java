package com.wovely.wovely.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import com.wovely.wovely.clients.CatalogClient;
import com.wovely.common.error.ApiException;
import com.wovely.wovely.models.EOrderStatus;
import com.wovely.wovely.models.Order;
import com.wovely.wovely.models.OrderItem;
import com.wovely.wovely.models.ReturnRequest;
import com.wovely.wovely.repository.OrderRepository;
import com.wovely.wovely.repository.ReturnRequestRepository;
import com.wovely.wovely.security.OwnershipGuard;
import com.wovely.wovely.support.Orders;

/** PLAN.md 2.6 — return lifecycle with restock and refund. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReturnServiceTest {

    @Mock ReturnRequestRepository returnRepository;
    @Mock OrderRepository orderRepository;
    @Mock CatalogClient catalogClient;
    @Mock com.wovely.wovely.clients.InventoryClient inventoryClient;
    @Mock OwnershipGuard ownership;
    @Mock AuditService auditService;
    @Mock WalletService walletService;

    ReturnService service;
    Order order;
    ReturnRequest ret;

    @BeforeEach
    void setUp() {
        OrderService orderService = new OrderService(); // only transition() is used
        orderService.walletService = walletService;
        orderService.inventoryClient = inventoryClient;
        service = new ReturnService(returnRepository, orderRepository, orderService, catalogClient, ownership, auditService,
                walletService, inventoryClient);
        order = Orders.order("o-1", "buyer-1", "seller-1", EOrderStatus.DELIVERED); // p-1 × 2 @ 10 = 20
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(returnRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ret = new ReturnRequest();
        ret.setId("r-1");
        ret.setOrderId("o-1");
        ret.setSellerId("seller-1");
        ret.setItems(List.of(new OrderItem("p-1", "Basket", 2, 1000L, "")));
        ret.setTotalCents(2000L);
        ret.setStatus("ARRIVED");
        when(returnRepository.findById("r-1")).thenReturn(Optional.of(ret));
    }

    static Date daysAgo(int days) {
        return Date.from(java.time.Instant.now().minus(java.time.Duration.ofDays(days)));
    }

    static com.wovely.wovely.payload.request.CreateReturnRequest returnOfP1() {
        return new com.wovely.wovely.payload.request.CreateReturnRequest("o-1",
            List.of(new com.wovely.wovely.payload.request.CreateReturnRequest.Item("p-1")), "Too small");
    }

    @Test
    void returnWindowIsThirtyDaysFromDelivery() {
        order.setCreatedAt(daysAgo(60)); // ordered long ago…
        order.setDeliveredAt(daysAgo(29)); // …but delivered 29 days ago: still open
        assertThat(service.create(returnOfP1()).getItems()).hasSize(1);
    }

    @Test
    void returnAfterTheWindowIs409AndChangesNothing() {
        order.setDeliveredAt(daysAgo(31));
        assertThatThrownBy(() -> service.create(returnOfP1()))
            .isInstanceOfSatisfying(ApiException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(e.getMessage()).contains("30-day return window");
            });
        assertThat(order.getItems()[0].isReturned()).isFalse();
        verify(returnRepository, never()).save(any());
    }

    @Test
    void legacyOrdersWithoutDeliveryDateUseTheOrderDate() {
        order.setDeliveredAt(null);
        order.setCreatedAt(daysAgo(45));
        assertThatThrownBy(() -> service.create(returnOfP1())).isInstanceOf(ApiException.class);
        order.setCreatedAt(daysAgo(3));
        assertThat(service.create(returnOfP1())).isNotNull();
    }

    @Test
    void deadlineIsOnlyExposedOnceReceived() {
        order.setDeliveredAt(daysAgo(0));
        assertThat(order.getReturnDeadline()).isAfter(daysAgo(-29)).isBefore(daysAgo(-31));
        order.setStatus(EOrderStatus.SHIPPED);
        assertThat(order.getReturnDeadline()).isNull();
    }

    @Test
    void returnsAreOnlyPossibleOnDeliveredOrders() {
        order.setStatus(EOrderStatus.SHIPPED);
        assertThatThrownBy(() -> service.create(returnOfP1()))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void fullRefundRestocksItemsRecordsAmountAndRefundsTheOrder() {
        ReturnRequest updated = service.updateStatus("r-1", "REFUNDED");
        assertThat(updated.getStatus()).isEqualTo("REFUNDED");
        verify(inventoryClient).release("seller-1", "p-1", 2);
        assertThat(order.getRefundedCents()).isEqualTo(2000L);
        assertThat(order.getStatus()).isEqualTo(EOrderStatus.REFUNDED);
    }

    @Test
    void fullReturnOfAnOrderWithDeliveryStillRefundsTheOrderAndItsDelivery() {
        // regression (newman): the total includes the delivery fee, which item refunds can never reach
        order.setDeliveryOption(com.wovely.wovely.models.EDeliveryOption.BICYCLE);
        order.setDeliveryFeeCents(1000L);
        order.recomputeTotal(); // 20 items + 10 delivery
        service.updateStatus("r-1", "REFUNDED");
        assertThat(order.getStatus()).isEqualTo(EOrderStatus.REFUNDED);
        verify(walletService).refundReturn(order, 2000L);
        verify(walletService).refundRemaining(order); // the delivery fee goes back too
    }

    @Test
    void partialReturnOfAnOrderWithDeliveryKeepsTheFee() {
        order.setDeliveryFeeCents(1000L);
        order.recomputeTotal();
        ret.setItems(List.of(new OrderItem("p-1", "Basket", 1, 1000L, "")));
        ret.setTotalCents(1000L);
        service.updateStatus("r-1", "REFUNDED");
        assertThat(order.getStatus()).isEqualTo(EOrderStatus.DELIVERED);
        verify(walletService, never()).refundRemaining(any());
    }

    @Test
    void partialRefundKeepsOrderStatus() {
        ret.setItems(List.of(new OrderItem("p-1", "Basket", 1, 1000L, "")));
        ret.setTotalCents(1000L);
        service.updateStatus("r-1", "REFUNDED");
        assertThat(order.getRefundedCents()).isEqualTo(1000L);
        assertThat(order.getStatus()).isEqualTo(EOrderStatus.DELIVERED);
    }

    @Test
    void deniedIsAcceptedAndDoesNotRestock() {
        ret.setStatus("PICKED_UP");
        assertThat(service.updateStatus("r-1", "DENIED").getStatus()).isEqualTo("DENIED");
        verify(inventoryClient, never()).release(anyString(), anyString(), anyInt());
    }

    @Test
    void terminalReturnCannotMoveAgain() {
        ret.setStatus("REFUNDED");
        assertThatThrownBy(() -> service.updateStatus("r-1", "ARRIVED"))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(inventoryClient, never()).release(anyString(), anyString(), anyInt());
    }

    @Test
    void cannotSkipFromPickedUpStraightToRefunded() {
        ret.setStatus("PICKED_UP");
        assertThatThrownBy(() -> service.updateStatus("r-1", "REFUNDED"))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void unknownStatusIsBadRequest() {
        assertThatThrownBy(() -> service.updateStatus("r-1", "REJECTED"))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
