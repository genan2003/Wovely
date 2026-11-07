package com.wovely.products.payload;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body for creating or editing a listing. On an edit every field is optional: a missing (null) field
 * keeps the stored value, so a client that doesn't know a field can't wipe it. Server-owned fields
 * (id, status, delivery emission, stock version) are not part of the request.
 *
 * <p>{@code sellerId} is only honoured when an admin creates a listing on a seller's behalf.
 */
public record ListingRequest(
        @Size(max = 200) String name,
        @Size(max = 5000) String description,
        /** RON with at most two decimals; more decimals are a 400, never rounded (PLAN.md 7.5). */
        @PositiveOrZero @jakarta.validation.constraints.Digits(integer = 7, fraction = 2) java.math.BigDecimal price,
        String sellerId,
        @Size(max = 2048) String imageUrl,
        @Size(max = 120) String category,
        @Size(max = 500) String categoryPath,
        @Size(max = 20) List<@NotBlank @Size(max = 80) String> materials,
        @Size(max = 120) String city,
        @Size(max = 120) String region,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @Size(max = 40) String co2EmissionScore,
        @Size(max = 80) String shippingMethod,
        @JsonProperty("isHandmade") @JsonAlias("handmade") Boolean isHandmade,
        Boolean localPickup,
        @PositiveOrZero Integer stockQuantity,
        @PositiveOrZero Integer lowStockThreshold) {
}
