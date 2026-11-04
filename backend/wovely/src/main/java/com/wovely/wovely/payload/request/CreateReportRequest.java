package com.wovely.wovely.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Report a listing or a conversation (PLAN.md 3.2). Reporter, status and display name are set by the server.
 *
 * @param targetType {@code CHAT} or {@code PRODUCT}
 * @param targetId   product id, or for chats the other participant's id (legacy {@code "userA:userB"} accepted)
 */
public record CreateReportRequest(
        @NotNull(message = "targetType is required") com.wovely.wovely.models.EReportTarget targetType,
        @NotBlank(message = "targetId is required") @Size(max = 200) String targetId,
        @NotBlank(message = "reason is required") @Size(max = 1000, message = "reason must be at most 1000 characters") String reason) {
}
