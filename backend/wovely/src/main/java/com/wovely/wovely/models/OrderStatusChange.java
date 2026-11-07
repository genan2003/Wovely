package com.wovely.wovely.models;

import java.util.Date;

/**
 * One step of an order's status timeline (PLAN.md 7.1). Appended by {@code OrderService#transition} (and when an
 * order is created) in the same document write as the status itself, so history and status never disagree.
 *
 * @param from      previous status, {@code null} for the creation entry
 * @param to        new status
 * @param at        when it happened
 * @param actorRole BUYER, SELLER, ADMIN or SYSTEM ({@code OrderStatusPolicy.Actor#name()})
 * @param actorId   user id of the caller, {@code "system"} for scheduled/derived changes; never sent to buyers or sellers
 * @param reason    optional free-text reason (cancellation, refund, intervention)
 */
public record OrderStatusChange(EOrderStatus from, EOrderStatus to, Date at, String actorRole, String actorId,
        String reason) {
}
