package com.wovely.wovely.services;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

/**
 * "Ship with Platform" (PLAN.md 3.6, README seller story): picks the lowest-CO₂ partner carrier able to
 * cover the route and issues a prepaid label with a tracking number.
 *
 * <p>Mock integration: there is no real carrier API. Route distance is a stable estimate derived from
 * the destination text (same address → same distance), so the choice is deterministic and testable.
 */
@Service
public class EcoCarrierService {

    /** A partner carrier. {@code maxRangeKm} bounds how far its low-emission fleet delivers. */
    public record EcoCarrier(String code, String name, String method, double co2PerKm, int maxRangeKm) {
    }

    /** What a generated label carries; stored on the order. */
    public record ShippingLabel(EcoCarrier carrier, String trackingNumber, int distanceKm, double estimatedCo2Kg,
            String text) {
    }

    static final List<EcoCarrier> CARRIERS = List.of(
            new EcoCarrier("GRL", "GreenLogistics", "Bicycle & Electric Van", 0.05, 40),
            new EcoCarrier("ECP", "EcoParcel", "Carbon-Neutral Fleet", 0.12, 900),
            new EcoCarrier("BIO", "BioShip", "Bio-fuel Trucks", 0.25, 3000),
            new EcoCarrier("EFD", "EarthFriendly Delivery", "Optimized Route Hybrid", 0.30, Integer.MAX_VALUE));

    private static final String TRACKING_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    /** Lowest CO₂ per km among the carriers whose range covers {@code distanceKm}. */
    public EcoCarrier selectBestCarrier(int distanceKm) {
        return CARRIERS.stream()
                .filter(c -> c.maxRangeKm() >= distanceKm)
                .min(Comparator.comparingDouble(EcoCarrier::co2PerKm))
                .orElse(CARRIERS.get(CARRIERS.size() - 1));
    }

    /**
     * Mock routing: a stable 5–1 200 km estimate from the destination text. Addresses mentioning the
     * maker's city would be local in a real integration; here only the text matters.
     */
    public int estimateDistanceKm(String destination) {
        String key = destination == null ? "" : destination.trim().toLowerCase(Locale.ROOT);
        return 5 + Math.floorMod(key.hashCode(), 1196);
    }

    public ShippingLabel generateLabel(String orderNumber, String destination) {
        int distance = estimateDistanceKm(destination);
        EcoCarrier carrier = selectBestCarrier(distance);
        double co2 = Math.round(carrier.co2PerKm() * distance * 100.0) / 100.0;
        String tracking = "WVL-" + carrier.code() + "-" + randomCode(10);
        String text = String.join("\n",
                "--- PREPAID ECO-SHIPPING LABEL ---",
                "CARRIER: " + carrier.name(),
                "METHOD: " + carrier.method(),
                "TRACKING: " + tracking,
                "ORDER #: " + orderNumber,
                "DESTINATION: " + (destination == null ? "" : destination),
                "DISTANCE: ~" + distance + " km",
                "EST. CO2: " + co2 + " kg",
                "----------------------------------");
        return new ShippingLabel(carrier, tracking, distance, co2, text);
    }

    private String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(TRACKING_ALPHABET.charAt(random.nextInt(TRACKING_ALPHABET.length())));
        }
        return sb.toString();
    }
}
