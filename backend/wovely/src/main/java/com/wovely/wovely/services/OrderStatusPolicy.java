package com.wovely.wovely.services;

import static com.wovely.wovely.models.EOrderStatus.CANCELLED;
import static com.wovely.wovely.models.EOrderStatus.COMPLETED;
import static com.wovely.wovely.models.EOrderStatus.CONFIRMED;
import static com.wovely.wovely.models.EOrderStatus.DELIVERED;
import static com.wovely.wovely.models.EOrderStatus.PENDING;
import static com.wovely.wovely.models.EOrderStatus.PROCESSING;
import static com.wovely.wovely.models.EOrderStatus.REFUNDED;
import static com.wovely.wovely.models.EOrderStatus.SHIPPED;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.wovely.wovely.models.EOrderStatus;

/**
 * Order lifecycle rules (PLAN.md 2.4, diagram {@code docs/diagrams/state/order-status.puml}).
 *
 * <pre>
 * PENDING ─┬─► CONFIRMED ─► PROCESSING ─► SHIPPED ─► DELIVERED ─► COMPLETED
 *          └──────────────────►┘
 * CANCELLED: buyer from PENDING/CONFIRMED, seller up to PROCESSING
 * REFUNDED / DISPUTED / anything else: admin only (intervention)
 * CANCELLED and REFUNDED are terminal for everyone.
 * </pre>
 */
public final class OrderStatusPolicy {

    public enum Actor { BUYER, SELLER, ADMIN, SYSTEM }

    private static final Set<EOrderStatus> TERMINAL = EnumSet.of(CANCELLED, REFUNDED);

    /** Statuses in which the goods are still with the seller: cancelling/refunding restocks. */
    private static final Set<EOrderStatus> NOT_SHIPPED = EnumSet.of(PENDING, CONFIRMED, PROCESSING);

    private static final Map<EOrderStatus, Map<EOrderStatus, Set<Actor>>> RULES = new EnumMap<>(EOrderStatus.class);

    static {
        allow(PENDING, CONFIRMED, Actor.SELLER);
        allow(PENDING, PROCESSING, Actor.SELLER);
        allow(PENDING, CANCELLED, Actor.BUYER, Actor.SELLER);
        allow(CONFIRMED, PROCESSING, Actor.SELLER);
        allow(CONFIRMED, CANCELLED, Actor.BUYER, Actor.SELLER);
        allow(PROCESSING, SHIPPED, Actor.SELLER);
        allow(PROCESSING, CANCELLED, Actor.SELLER);
        allow(SHIPPED, DELIVERED, Actor.BUYER, Actor.SELLER);
        allow(DELIVERED, COMPLETED, Actor.BUYER, Actor.SELLER);
    }

    private OrderStatusPolicy() {
    }

    private static void allow(EOrderStatus from, EOrderStatus to, Actor... actors) {
        RULES.computeIfAbsent(from, k -> new EnumMap<>(EOrderStatus.class)).put(to, Set.of(actors));
    }

    /** Whether {@code actor} may move an order from {@code from} to {@code to}. */
    public static boolean isAllowed(EOrderStatus from, EOrderStatus to, Actor actor) {
        if (from == to || TERMINAL.contains(from)) {
            return false;
        }
        if (actor == Actor.ADMIN || actor == Actor.SYSTEM) {
            return true;
        }
        return RULES.getOrDefault(from, Map.of()).getOrDefault(to, Set.of()).contains(actor);
    }

    public static boolean isTerminal(EOrderStatus status) {
        return TERMINAL.contains(status);
    }

    /** Goods never left the seller, so stock must be restored when the order is cancelled/refunded. */
    public static boolean restocksOn(EOrderStatus from, EOrderStatus to) {
        return (to == CANCELLED || to == REFUNDED) && NOT_SHIPPED.contains(from);
    }

    /** Items/address may only be edited before processing starts. */
    public static boolean isEditable(EOrderStatus status) {
        return status == PENDING || status == CONFIRMED;
    }
}
