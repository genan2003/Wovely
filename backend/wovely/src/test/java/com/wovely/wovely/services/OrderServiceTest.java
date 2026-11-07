package com.wovely.wovely.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import com.wovely.wovely.clients.CatalogClient;
import com.wovely.wovely.clients.CatalogClient.ProductSnapshot;
import com.wovely.common.error.ApiException;
import com.wovely.wovely.models.EOrderStatus;
import com.wovely.wovely.models.Order;
import com.wovely.wovely.models.OrderItem;
import com.wovely.wovely.payload.request.CreateOrderRequest;
import com.wovely.wovely.payload.request.OrderInterventionRequest;
import com.wovely.wovely.payload.request.UpdateOrderRequest;
import com.wovely.wovely.payload.response.OrderDTO;
import com.wovely.wovely.repository.OrderRepository;
import com.wovely.wovely.services.OrderStatusPolicy.Actor;
import com.wovely.wovely.support.Orders;

/** PLAN.md 2.3 (interventions), 2.4 (transitions), 2.5 (edit reconciles stock). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock CatalogClient catalogClient;
    @Mock com.wovely.wovely.clients.InventoryClient inventoryClient;
    @Mock AuditService auditService;
    @Mock EcoCarrierService ecoCarrierService;
    @Mock AddressService addressService;
    @Mock WalletService walletService;
    @InjectMocks OrderService service;

    Order order;

    @BeforeEach
    void setUp() {
        order = Orders.order("o-1", "buyer-1", "seller-1", EOrderStatus.SHIPPED); // 1 item: p-1 × 2 @ 10.0
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static OrderInterventionRequest intervention(String action, EOrderStatus newStatus, String reason) {
        OrderInterventionRequest r = new OrderInterventionRequest();
        r.setAction(action);
        r.setNewStatus(newStatus);
        r.setReason(reason);
        return r;
    }

    private Order saved() {
        ArgumentCaptor<Order> c = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(1)).save(c.capture());
        return c.getValue();
    }

    // ---------------------------------------------------------------- 2.3 interventions

    @Test
    void markDisputedPersistsFlagReasonAndStatusInOneSave() {
        service.applyIntervention("o-1", intervention("MARK_DISPUTED", null, "Item never arrived"));
        Order s = saved();
        assertThat(s.getStatus()).isEqualTo(EOrderStatus.DISPUTED);
        assertThat(s.getIsDisputed()).isTrue();
        assertThat(s.getDisputeReason()).isEqualTo("Item never arrived");
        verify(auditService, times(1)).record(eq("ORDER_INTERVENTION"), eq("o-1"), contains("SHIPPED -> DISPUTED"));
    }

    @Test
    void resolveDisputeClearsFlagAndMovesToNewStatus() {
        order.setStatus(EOrderStatus.DISPUTED);
        order.setIsDisputed(true);
        order.setDisputeReason("x");
        service.applyIntervention("o-1", intervention("RESOLVE_DISPUTE", EOrderStatus.DELIVERED, "Proof of delivery"));
        Order s = saved();
        assertThat(s.getIsDisputed()).isFalse();
        assertThat(s.getDisputeReason()).isNull();
        assertThat(s.getStatus()).isEqualTo(EOrderStatus.DELIVERED);
    }

    @Test
    void resolveDisputeWithoutNewStatusIsBadRequestAndNothingIsSaved() {
        order.setStatus(EOrderStatus.DISPUTED);
        order.setIsDisputed(true);
        assertThatThrownBy(() -> service.applyIntervention("o-1", intervention("RESOLVE_DISPUTE", null, null)))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void resolvingAnUndisputedOrderIsConflict() {
        assertThatThrownBy(() -> service.applyIntervention("o-1", intervention("RESOLVE_DISPUTE", EOrderStatus.DELIVERED, null)))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void unknownActionIsBadRequest() {
        assertThatThrownBy(() -> service.applyIntervention("o-1", intervention("TELEPORT", null, null)))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void forceRefundAfterShippingDoesNotRestock() {
        service.applyIntervention("o-1", intervention("FORCE_REFUND", null, "Lost parcel"));
        assertThat(saved().getStatus()).isEqualTo(EOrderStatus.REFUNDED);
        verify(inventoryClient, never()).release(anyString(), anyString(), anyInt());
    }

    @Test
    void forceRefundBeforeShippingRestocks() {
        order.setStatus(EOrderStatus.PROCESSING);
        service.applyIntervention("o-1", intervention("FORCE_REFUND", null, "Seller unavailable"));
        verify(inventoryClient).release("seller-1", "p-1", 2);
    }

    @Test
    void adminCannotReopenCancelledOrder() {
        order.setStatus(EOrderStatus.CANCELLED);
        assertThatThrownBy(() -> service.applyIntervention("o-1", intervention("UPDATE_STATUS", EOrderStatus.PENDING, null)))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void interventionIsAuditedExactlyOnce() {
        service.applyIntervention("o-1", intervention("UPDATE_STATUS", EOrderStatus.DELIVERED, "carrier confirmed"));
        verify(auditService, times(1)).record(anyString(), anyString(), anyString());
    }

    // ---------------------------------------------------------------- 2.4 transitions via buyer/seller

    @Test
    void buyerCannotConfirmReceiptBeforeShipping() {
        order.setStatus(EOrderStatus.PENDING);
        assertThatThrownBy(() -> service.updateOrderStatus("o-1", EOrderStatus.DELIVERED, null, null, Actor.BUYER))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void repeatingTheCurrentStatusIsANoOp() {
        order.setStatus(EOrderStatus.CANCELLED);
        OrderDTO dto = service.updateOrderStatus("o-1", EOrderStatus.CANCELLED, "again", null, Actor.BUYER);
        assertThat(dto.getStatus()).isEqualTo(EOrderStatus.CANCELLED);
        verify(orderRepository, never()).save(any());
        verify(inventoryClient, never()).release(anyString(), anyString(), anyInt());
    }

    @Test
    void sameStatusWithNewEtaOnlyUpdatesEta() {
        order.setStatus(EOrderStatus.PROCESSING);
        service.updateOrderStatus("o-1", EOrderStatus.PROCESSING, null, 4, Actor.SELLER);
        assertThat(saved().getEstimatedDeliveryDays()).isEqualTo(4);
    }

    // ---------------------------------------------------------------- 2.5 edit reconciles stock

    private static UpdateOrderRequest items(CreateOrderRequest.Item... items) {
        UpdateOrderRequest r = new UpdateOrderRequest();
        r.setItems(List.of(items));
        return r;
    }

    @Test
    void increasingQuantityDeductsOnlyTheDeltaAndRecomputesTotal() {
        order.setStatus(EOrderStatus.PENDING);
        OrderDTO dto = service.updateOrderPreProcessing("o-1", items(new CreateOrderRequest.Item("p-1", 5)));
        verify(inventoryClient).reserve("seller-1", "p-1", 3);
        assertThat(dto.getTotalCents()).isEqualTo(5000L);
    }

    @Test
    void decreasingQuantityRestocksTheDifference() {
        order.setStatus(EOrderStatus.CONFIRMED);
        service.updateOrderPreProcessing("o-1", items(new CreateOrderRequest.Item("p-1", 1)));
        verify(inventoryClient).release("seller-1", "p-1", 1);
        verify(inventoryClient, never()).reserve(anyString(), anyString(), anyInt());
    }

    @Test
    void replacingAnItemRestocksTheOldAndPricesTheNewFromTheCatalog() {
        order.setStatus(EOrderStatus.PENDING);
        when(catalogClient.getProduct("p-2")).thenReturn(new ProductSnapshot("p-2", "Mug", 750L, "seller-1", "i", "APPROVED", 9));
        OrderDTO dto = service.updateOrderPreProcessing("o-1", items(new CreateOrderRequest.Item("p-2", 2)));
        verify(inventoryClient).reserve("seller-1", "p-2", 2);
        verify(inventoryClient).release("seller-1", "p-1", 2);
        assertThat(dto.getItems()).singleElement().satisfies(i -> assertThat(i.getPriceCents()).isEqualTo(750L));
        assertThat(dto.getTotalCents()).isEqualTo(1500L);
    }

    @Test
    void failedDeductionIsCompensatedAndOrderUnchanged() {
        order.setStatus(EOrderStatus.PENDING);
        order.setItems(new OrderItem[] { new OrderItem("p-1", "Basket", 1, 1000L, ""), new OrderItem("p-3", "Rug", 1, 3000L, "") });
        doThrow(ApiException.conflict("Insufficient stock for product p-3")).when(inventoryClient).reserve("seller-1", "p-3", 4);
        assertThatThrownBy(() -> service.updateOrderPreProcessing("o-1",
                items(new CreateOrderRequest.Item("p-1", 2), new CreateOrderRequest.Item("p-3", 5))))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(inventoryClient).release("seller-1", "p-1", 1);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void addingAnotherSellersProductIsRejected() {
        order.setStatus(EOrderStatus.PENDING);
        when(catalogClient.getProduct("p-9")).thenReturn(new ProductSnapshot("p-9", "Other", 100L, "seller-2", "", "APPROVED", 9));
        assertThatThrownBy(() -> service.updateOrderPreProcessing("o-1",
                items(new CreateOrderRequest.Item("p-1", 2), new CreateOrderRequest.Item("p-9", 1))))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void editIsLockedOnceTheOrderMovesBeyondProcessing() {
        order.setStatus(EOrderStatus.SHIPPED);
        assertThatThrownBy(() -> service.updateOrderPreProcessing("o-1", items(new CreateOrderRequest.Item("p-1", 1))))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void emptyItemListIsRejected() {
        order.setStatus(EOrderStatus.PENDING);
        assertThatThrownBy(() -> service.updateOrderPreProcessing("o-1", items()))
            .isInstanceOf(ApiException.class);
    }

    // ---------------------------------------------------------------- 3.6 ship with platform

    private static final EcoCarrierService.ShippingLabel LABEL = new EcoCarrierService.ShippingLabel(
            new EcoCarrierService.EcoCarrier("ECP", "EcoParcel", "Carbon-Neutral Fleet", 0.12, 900),
            "WVL-ECP-ABCDEFGHJK", 120, 14.4, "--- LABEL ---");

    private com.wovely.wovely.models.Order labelOrder(EOrderStatus status) {
        var o = com.wovely.wovely.support.Orders.order("o-9", "buyer-1", "seller-1", status);
        o.setShippingAddress("Str. Florilor 3, Sibiu");
        when(orderRepository.findById("o-9")).thenReturn(java.util.Optional.of(o));
        when(orderRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        when(ecoCarrierService.generateLabel(o.getOrderNumber(), "Str. Florilor 3, Sibiu")).thenReturn(LABEL);
        return o;
    }

    @Test
    void labelStoresCarrierTrackingAndCo2AndIsAudited() {
        labelOrder(EOrderStatus.PROCESSING);
        var dto = service.generateEcoShippingLabel("o-9", false);
        org.assertj.core.api.Assertions.assertThat(dto.getTrackingNumber()).isEqualTo("WVL-ECP-ABCDEFGHJK");
        org.assertj.core.api.Assertions.assertThat(dto.getCarrierName()).isEqualTo("EcoParcel");
        org.assertj.core.api.Assertions.assertThat(dto.getEstimatedCo2Kg()).isEqualTo(14.4);
        org.assertj.core.api.Assertions.assertThat(dto.getShippingDistanceKm()).isEqualTo(120);
        org.assertj.core.api.Assertions.assertThat(dto.getEcoShippingLabel()).isEqualTo("--- LABEL ---");
        org.assertj.core.api.Assertions.assertThat(dto.getLabelGeneratedAt()).isNotNull();
        verify(auditService).record(org.mockito.ArgumentMatchers.eq("SHIPPING_LABEL_GENERATED"),
                org.mockito.ArgumentMatchers.eq("o-9"), org.mockito.ArgumentMatchers.contains("WVL-ECP-ABCDEFGHJK"));
    }

    @Test
    void sellerCannotBuyASecondLabelButAdminCanRegenerate() {
        var o = labelOrder(EOrderStatus.CONFIRMED);
        o.setEcoShippingLabel("old");
        o.setTrackingNumber("WVL-GRL-OLD");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.generateEcoShippingLabel("o-9", false))
            .isInstanceOf(com.wovely.common.error.ApiException.class).hasMessageContaining("already");
        var dto = service.regenerateShippingLabel("o-9");
        org.assertj.core.api.Assertions.assertThat(dto.getTrackingNumber()).isEqualTo("WVL-ECP-ABCDEFGHJK");
        verify(auditService).record(org.mockito.ArgumentMatchers.eq("SHIPPING_LABEL_REGENERATED"),
                org.mockito.ArgumentMatchers.eq("o-9"), org.mockito.ArgumentMatchers.contains("was WVL-GRL-OLD"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = EOrderStatus.class, names = { "CONFIRMED", "PROCESSING" },
            mode = org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE)
    void labelsOnlyBeforeShipping(EOrderStatus status) {
        labelOrder(status);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.generateEcoShippingLabel("o-9", false))
            .isInstanceOf(com.wovely.common.error.ApiException.class)
            .extracting("status").isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
        verify(ecoCarrierService, never()).generateLabel(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unknownOrderIs404() {
        when(orderRepository.findById("nope")).thenReturn(java.util.Optional.empty());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.generateEcoShippingLabel("nope", false))
            .extracting("status").isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void sellerCannotShipWithoutTrackingNumber() {
        var o = labelOrder(EOrderStatus.PROCESSING);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.transition(o, EOrderStatus.SHIPPED, OrderStatusPolicy.Actor.SELLER, null))
            .extracting("status").isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
        o.setTrackingNumber("WVL-ECP-X");
        service.transition(o, EOrderStatus.SHIPPED, OrderStatusPolicy.Actor.SELLER, null);
        org.assertj.core.api.Assertions.assertThat(o.getStatus()).isEqualTo(EOrderStatus.SHIPPED);
    }

    // ---------------------------------------------------------------- 3.7 wallet hooks

    @Test
    void cancelRefundsAndCompletePaysOut() {
        var cancel = com.wovely.wovely.support.Orders.order("o-c", "buyer-1", "seller-1", EOrderStatus.PENDING);
        service.transition(cancel, EOrderStatus.CANCELLED, OrderStatusPolicy.Actor.BUYER, "no");
        verify(walletService).refundRemaining(cancel);

        var done = com.wovely.wovely.support.Orders.order("o-d", "buyer-1", "seller-1", EOrderStatus.DELIVERED);
        service.transition(done, EOrderStatus.COMPLETED, OrderStatusPolicy.Actor.BUYER, null);
        verify(walletService).releaseToSeller(done);
        verify(walletService, never()).refundRemaining(done);
    }

    @Test
    void deliveryStampsTheStartOfTheReturnWindowOnce() {
        var shipped = com.wovely.wovely.support.Orders.order("o-s", "buyer-1", "seller-1", EOrderStatus.SHIPPED);
        org.assertj.core.api.Assertions.assertThat(shipped.getDeliveredAt()).isNull();
        service.transition(shipped, EOrderStatus.DELIVERED, OrderStatusPolicy.Actor.BUYER, null);
        java.util.Date delivered = shipped.getDeliveredAt();
        org.assertj.core.api.Assertions.assertThat(delivered).isNotNull();
        service.transition(shipped, EOrderStatus.COMPLETED, OrderStatusPolicy.Actor.BUYER, null);
        org.assertj.core.api.Assertions.assertThat(shipped.getDeliveredAt()).isSameAs(delivered);
    }

    @Test
    void sellerItemEditsKeepTheDeliveryFeeInTheTotal() {
        var o = com.wovely.wovely.support.Orders.order("o-f", "buyer-1", "seller-1", EOrderStatus.PENDING); // p-1 × 2 @ 10
        o.setDeliveryOption(com.wovely.wovely.models.EDeliveryOption.ELECTRIC_VAN);
        o.setDeliveryFeeCents(1500L);
        o.recomputeTotal();
        org.assertj.core.api.Assertions.assertThat(o.getTotalCents()).isEqualTo(3500L);
        o.getItems()[0].setQuantity(1);
        o.recomputeTotal();
        org.assertj.core.api.Assertions.assertThat(o.getTotalCents()).isEqualTo(2500L);
        o.setDeliveryFeeCents(null); // manual sales and legacy orders: items only
        o.recomputeTotal();
        org.assertj.core.api.Assertions.assertThat(o.getTotalCents()).isEqualTo(1000L);
    }
}
