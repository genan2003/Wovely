package com.wovely.products.models;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** PLAN.md 3.5 — the server-side delivery emission rule. */
class EDeliveryEmissionTest {

    @ParameterizedTest(name = "{0} / co2={1} / pickup={2} -> {3}")
    @CsvSource(nullValues = "null", value = {
        "Local Pickup,          High,   null,  ZERO",
        "Bicycle Courier,       Medium, false, ZERO",
        "Bike messenger,        null,   null,  ZERO",
        "Delivered on foot,     High,   null,  ZERO",
        "Express,               High,   true,  ZERO",
        "Carbon Neutral,        High,   false, LOW",
        "carbon-neutral courier,null,   null,  LOW",
        "Electric Van,          Medium, null,  LOW",
        "Standard Eco-Post,     Medium, null,  LOW",
        "Standard,              Low,    null,  LOW",
        "Standard,              ' low ',null,  LOW",
        "Express,               High,   false, STANDARD",
        "Standard,              Medium, null,  STANDARD",
        "null,                  null,   null,  STANDARD",
        "Reverse logistics,     High,   null,  STANDARD",
    })
    void classify(String method, String co2, Boolean pickup, EDeliveryEmission expected) {
        assertThat(EDeliveryEmission.classify(method, co2, pickup)).isEqualTo(expected);
    }

    @org.junit.jupiter.api.Test
    void ordering() {
        assertThat(EDeliveryEmission.ZERO.atMost(EDeliveryEmission.LOW)).isTrue();
        assertThat(EDeliveryEmission.LOW.atMost(EDeliveryEmission.LOW)).isTrue();
        assertThat(EDeliveryEmission.STANDARD.atMost(EDeliveryEmission.LOW)).isFalse();
    }
}
