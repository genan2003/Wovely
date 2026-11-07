package com.wovely.wovely.payload.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Buyer's review of a seller; author and "verified purchase" are decided by the server. */
public record SellerReviewRequest(
        @NotNull @Min(1) @Max(5) Integer overallRating,
        @NotNull @Min(1) @Max(5) Integer communicationRating,
        @NotNull @Min(1) @Max(5) Integer shippingRating,
        @Size(max = 2000) String comment) {
}
