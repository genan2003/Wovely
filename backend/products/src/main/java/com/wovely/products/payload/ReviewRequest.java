package com.wovely.products.payload;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Product review; author and "verified purchase" are decided by the server. */
public record ReviewRequest(
        @NotBlank(message = "productId is required") @Size(max = 64) String productId,
        @NotNull(message = "rating is required") @Min(value = 1, message = "rating must be between 1 and 5")
        @Max(value = 5, message = "rating must be between 1 and 5") Integer rating,
        @Size(max = 2000) String comment,
        @Size(max = 6, message = "at most 6 photos") List<@NotBlank @Size(max = 2048) String> photoUrls) {
}
