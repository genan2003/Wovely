package com.wovely.wovely.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wovely.wovely.models.EOrderStatus;
import com.wovely.wovely.repository.OrderRepository;

/** PLAN.md 3.3 — the "Verified Purchase" rule. */
@ExtendWith(MockitoExtension.class)
class PurchaseVerificationServiceTest {

    @Mock OrderRepository orderRepository;
    @InjectMocks PurchaseVerificationService service;

    @Test
    void onlyDeliveredOrCompletedOrdersCount() {
        assertThat(PurchaseVerificationService.RECEIVED)
            .containsExactlyInAnyOrder(EOrderStatus.DELIVERED, EOrderStatus.COMPLETED);
    }

    @Test
    void delegatesProductCheckWithReceivedStatuses() {
        when(orderRepository.existsByBuyerIdAndStatusInAndItemsProductId("b", PurchaseVerificationService.RECEIVED, "p"))
            .thenReturn(true);
        assertThat(service.hasReceivedProduct("b", "p")).isTrue();
    }

    @Test
    void delegatesSellerCheckWithReceivedStatuses() {
        when(orderRepository.existsByBuyerIdAndSellerIdAndStatusIn("b", "s", PurchaseVerificationService.RECEIVED))
            .thenReturn(false);
        assertThat(service.hasReceivedFromSeller("b", "s")).isFalse();
    }

    @Test
    void blankIdsAreNeverVerifiedAndSkipTheQuery() {
        assertThat(service.hasReceivedProduct(null, "p")).isFalse();
        assertThat(service.hasReceivedProduct("b", " ")).isFalse();
        assertThat(service.hasReceivedFromSeller("", "s")).isFalse();
        verifyNoInteractions(orderRepository);
    }

}
