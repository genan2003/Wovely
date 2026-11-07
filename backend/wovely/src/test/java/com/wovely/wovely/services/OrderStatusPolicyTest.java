package com.wovely.wovely.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.wovely.wovely.models.EOrderStatus;
import com.wovely.wovely.services.OrderStatusPolicy.Actor;

/** PLAN.md 2.4 — transition matrix. */
class OrderStatusPolicyTest {

    @ParameterizedTest(name = "{2}: {0} -> {1} allowed={3}")
    @CsvSource({
        // happy path
        "PENDING,    CONFIRMED,  SELLER, true",
        "PENDING,    PROCESSING, SELLER, true",
        "CONFIRMED,  PROCESSING, SELLER, true",
        "PROCESSING, SHIPPED,    SELLER, true",
        "SHIPPED,    DELIVERED,  BUYER,  true",
        "SHIPPED,    DELIVERED,  SELLER, true",
        "DELIVERED,  COMPLETED,  BUYER,  true",
        // cancellation
        "PENDING,    CANCELLED,  BUYER,  true",
        "CONFIRMED,  CANCELLED,  BUYER,  true",
        "PROCESSING, CANCELLED,  BUYER,  false",
        "PROCESSING, CANCELLED,  SELLER, true",
        "SHIPPED,    CANCELLED,  SELLER, false",
        // role limits
        "PENDING,    DELIVERED,  BUYER,  false",
        "PENDING,    PROCESSING, BUYER,  false",
        "PROCESSING, REFUNDED,   SELLER, false",
        "SHIPPED,    DISPUTED,   BUYER,  false",
        "SHIPPED,    PENDING,    SELLER, false",
        // admin can intervene, but terminal states stay terminal
        "SHIPPED,    REFUNDED,   ADMIN,  true",
        "DELIVERED,  DISPUTED,   ADMIN,  true",
        "DISPUTED,   COMPLETED,  ADMIN,  true",
        "CANCELLED,  PENDING,    ADMIN,  false",
        "REFUNDED,   DELIVERED,  ADMIN,  false",
        "CANCELLED,  PENDING,    BUYER,  false",
        // no-op is not a transition
        "PENDING,    PENDING,    SELLER, false",
    })
    void transitions(EOrderStatus from, EOrderStatus to, Actor actor, boolean allowed) {
        assertThat(OrderStatusPolicy.isAllowed(from, to, actor)).isEqualTo(allowed);
    }

    @ParameterizedTest(name = "{0} -> {1} restocks={2}")
    @CsvSource({
        "PENDING,    CANCELLED, true",
        "PROCESSING, CANCELLED, true",
        "PROCESSING, REFUNDED,  true",
        "SHIPPED,    CANCELLED, false",
        "DELIVERED,  REFUNDED,  false",
        "PENDING,    PROCESSING, false",
    })
    void restockOnlyWhileGoodsAreWithTheSeller(EOrderStatus from, EOrderStatus to, boolean restocks) {
        assertThat(OrderStatusPolicy.restocksOn(from, to)).isEqualTo(restocks);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(EOrderStatus.class)
    void editableUntilTheOrderMovesBeyondProcessing(EOrderStatus status) {
        boolean expected = status == EOrderStatus.PENDING || status == EOrderStatus.CONFIRMED
                || status == EOrderStatus.PROCESSING;
        org.assertj.core.api.Assertions.assertThat(OrderStatusPolicy.isEditable(status)).isEqualTo(expected);
    }

    @org.junit.jupiter.api.Test
    void aLabelOrTrackingNumberLocksTheOrder() {
        var o = com.wovely.wovely.support.Orders.order("o", "b", "s", EOrderStatus.PROCESSING);
        org.assertj.core.api.Assertions.assertThat(OrderStatusPolicy.isEditable(o)).isTrue();
        o.setTrackingNumber("WVL-X");
        org.assertj.core.api.Assertions.assertThat(OrderStatusPolicy.isEditable(o)).isFalse();
        o.setTrackingNumber(null);
        o.setEcoShippingLabel("label");
        org.assertj.core.api.Assertions.assertThat(OrderStatusPolicy.isEditable(o)).isFalse();
    }
}
