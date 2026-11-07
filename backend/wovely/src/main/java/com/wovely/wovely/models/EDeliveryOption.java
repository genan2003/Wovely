package com.wovely.wovely.models;

/**
 * Delivery a buyer picks per order at checkout. The server owns the price list: the fee is added to
 * the order total and charged with it (the SPA reads this table from GET /api/public/delivery-options).
 * CO₂ is a per-order estimate used for display only.
 */
public enum EDeliveryOption {
    BICYCLE("Bicycle delivery", 1000, 0.1, 2),
    ELECTRIC_VAN("Electric van", 1500, 0.5, 3),
    CARBON_NEUTRAL_COURIER("Carbon-neutral courier", 2500, 1.2, 2);

    private final String label;
    private final long feeCents;
    private final double co2Kg;
    private final int estimatedDays;

    EDeliveryOption(String label, long feeCents, double co2Kg, int estimatedDays) {
        this.label = label;
        this.feeCents = feeCents;
        this.co2Kg = co2Kg;
        this.estimatedDays = estimatedDays;
    }

    public String label() { return label; }
    public long feeCents() { return feeCents; }
    /** Fee in RON for display (scale 2). */
    public java.math.BigDecimal fee() { return java.math.BigDecimal.valueOf(feeCents, 2); }
    public double co2Kg() { return co2Kg; }
    public int estimatedDays() { return estimatedDays; }
}
