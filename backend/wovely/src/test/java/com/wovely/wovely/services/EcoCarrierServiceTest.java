package com.wovely.wovely.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** PLAN.md 3.6 — lowest-CO₂ carrier that covers the route; labels carry a tracking number. */
class EcoCarrierServiceTest {

    final EcoCarrierService service = new EcoCarrierService();

    @ParameterizedTest(name = "{0} km -> {1}")
    @CsvSource({ "5, GreenLogistics", "40, GreenLogistics", "41, EcoParcel", "900, EcoParcel", "901, BioShip",
            "3001, EarthFriendly Delivery" })
    void picksTheCleanestCarrierInRange(int km, String expected) {
        assertThat(service.selectBestCarrier(km).name()).isEqualTo(expected);
    }

    @Test
    void distanceEstimateIsStableAndBounded() {
        int a = service.estimateDistanceKm("Str. Florilor 3, Sibiu");
        assertThat(service.estimateDistanceKm("  str. florilor 3, SIBIU ")).isEqualTo(a);
        assertThat(a).isBetween(5, 1200);
        assertThat(service.estimateDistanceKm(null)).isBetween(5, 1200);
    }

    @Test
    void labelHasCarrierTrackingAndCo2() {
        var label = service.generateLabel("ORD-1", "Str. Florilor 3, Sibiu");
        assertThat(label.trackingNumber()).matches("WVL-(GRL|ECP|BIO|EFD)-[A-Z2-9]{10}");
        assertThat(label.trackingNumber()).contains(label.carrier().code());
        assertThat(label.estimatedCo2Kg())
            .isEqualTo(Math.round(label.carrier().co2PerKm() * label.distanceKm() * 100.0) / 100.0);
        assertThat(label.text()).contains("ORD-1", label.trackingNumber(), label.carrier().name());
        assertThat(service.generateLabel("ORD-1", "Str. Florilor 3, Sibiu").trackingNumber())
            .as("each label gets a fresh tracking number").isNotEqualTo(label.trackingNumber());
    }
}
