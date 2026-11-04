package com.wovely.products.models;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * How much CO₂ the delivery of a listing emits (PLAN.md 3.5, README buyer story "Zero-Emission / Eco-friendly").
 * Always derived by the server from the shipping method, the seller's CO₂ score and local pickup, so a
 * seller can't simply label a listing "zero-emission".
 */
public enum EDeliveryEmission {
    /** Local pickup, bicycle courier or on foot. */
    ZERO,
    /** Carbon-neutral / electric delivery, or a "Low" CO₂ score. */
    LOW,
    STANDARD;

    private static final Pattern ZERO_METHODS = Pattern.compile("pick ?up|bicycle|bike|on foot|walk");
    private static final Pattern LOW_METHODS = Pattern.compile("carbon[ -]?neutral|electric|\\bev\\b|eco");

    public static EDeliveryEmission classify(String shippingMethod, String co2EmissionScore, Boolean localPickup) {
        String method = shippingMethod == null ? "" : shippingMethod.toLowerCase(Locale.ROOT);
        if (Boolean.TRUE.equals(localPickup) || ZERO_METHODS.matcher(method).find()) {
            return ZERO;
        }
        if (LOW_METHODS.matcher(method).find() || "low".equalsIgnoreCase(trim(co2EmissionScore))) {
            return LOW;
        }
        return STANDARD;
    }

    /** True if this level is at least as clean as {@code max} (ZERO ≤ LOW ≤ STANDARD). */
    public boolean atMost(EDeliveryEmission max) {
        return ordinal() <= max.ordinal();
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
