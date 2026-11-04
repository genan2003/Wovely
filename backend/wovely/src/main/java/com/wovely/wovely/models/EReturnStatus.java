package com.wovely.wovely.models;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Return lifecycle (PLAN.md 2.6, diagram {@code docs/diagrams/state/return-status.puml}).
 * Names match the existing UI (seller orders page and admin returns management).
 *
 * <pre>
 * PICKED_UP ─► ARRIVED ─► INSPECTION ─► REFUNDED
 *     │           │  └──────────────────►┘
 *     └───────────┴──────────┴──────────► DENIED
 * </pre>
 * REFUNDED restocks the returned items and adds the amount to the order's refundedAmount.
 */
public enum EReturnStatus {
    PICKED_UP, ARRIVED, INSPECTION, REFUNDED, DENIED;

    private static final Map<EReturnStatus, Set<EReturnStatus>> NEXT = Map.of(
            PICKED_UP, EnumSet.of(ARRIVED, DENIED),
            ARRIVED, EnumSet.of(INSPECTION, REFUNDED, DENIED),
            INSPECTION, EnumSet.of(REFUNDED, DENIED),
            REFUNDED, EnumSet.noneOf(EReturnStatus.class),
            DENIED, EnumSet.noneOf(EReturnStatus.class));

    public boolean canMoveTo(EReturnStatus next) {
        return NEXT.get(this).contains(next);
    }

    public boolean isTerminal() {
        return NEXT.get(this).isEmpty();
    }

    /** Lenient parse for legacy documents; unknown values are treated as the initial state. */
    public static EReturnStatus fromStored(String value) {
        if (value == null) {
            return PICKED_UP;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return PICKED_UP;
        }
    }
}
