package com.wovely.wovely.payload.response;

import java.util.Date;

import com.wovely.wovely.models.EOrderStatus;
import com.wovely.wovely.models.OrderStatusChange;

/**
 * One step of the order timeline as clients see it (PLAN.md 7.1). Carries the actor's <em>role</em> only — who
 * exactly acted (e.g. which admin) stays in the audit trail.
 *
 * @param inferred true when the step was read from stored dates of an order older than the timeline
 */
public record OrderTimelineEntryDTO(EOrderStatus from, EOrderStatus status, Date at, String actor, String reason,
        boolean inferred) {

    public static OrderTimelineEntryDTO of(OrderStatusChange c) {
        return new OrderTimelineEntryDTO(c.from(), c.to(), c.at(), c.actorRole(), c.reason(), c.actorRole() == null);
    }
}
