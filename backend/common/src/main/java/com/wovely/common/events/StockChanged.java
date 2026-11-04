package com.wovely.common.events;

import java.time.Instant;

/**
 * State-transfer event published by inventory, the single owner of stock (PLAN.md 4.2, ADR 0001).
 * Carries the full current level, not a delta, plus a per-product {@code version} that only grows:
 * consumers apply an event only if its version is newer than what they hold, so duplicates and
 * out-of-order delivery are harmless.
 */
public record StockChanged(String productId, String sellerId, int stockQuantity, int lowStockThreshold,
        boolean active, long version, Instant occurredAt) {

    /** Topic exchange owned by inventory. */
    public static final String EXCHANGE = "wovely.inventory";
    public static final String ROUTING_KEY = "stock.changed";
    /** Dead-letter exchange for messages a consumer cannot process. */
    public static final String DLX = "wovely.dlx";
}
