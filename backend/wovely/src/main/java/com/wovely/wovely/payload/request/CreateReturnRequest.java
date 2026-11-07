package com.wovely.wovely.payload.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Buyer opens a return. Only the order and which of its products come back are taken from the
 * client; buyer, seller, quantities, prices, total and status come from the stored order.
 */
public record CreateReturnRequest(
        @NotBlank(message = "orderId is required") @Size(max = 64) String orderId,
        @NotEmpty(message = "choose at least one item") @Size(max = 50) List<@Valid Item> items,
        @Size(max = 1000) String reason) {

    public record Item(@NotBlank(message = "productId is required") @Size(max = 64) String productId) {
    }

    public List<String> productIds() {
        return items == null ? List.of() : items.stream().map(Item::productId).toList();
    }
}
