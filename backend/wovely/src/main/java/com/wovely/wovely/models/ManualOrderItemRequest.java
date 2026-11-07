package com.wovely.wovely.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Individual item in a manual order request.
 */
public class ManualOrderItemRequest {

    @NotBlank(message = "Product ID is required")
    private String productId;

    @NotBlank(message = "Product name is required")
    private String productName;

    @NotNull(message = "Quantity is required")
    private Integer quantity;

    /** RON with at most two decimals (PLAN.md 7.5): more decimals are a 400, never rounded. */
    @NotNull(message = "Price is required")
    @jakarta.validation.constraints.PositiveOrZero
    @jakarta.validation.constraints.Digits(integer = 7, fraction = 2, message = "Price can have at most 2 decimals")
    private java.math.BigDecimal price;

    private String imageUrl;

    // Getters and Setters
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public java.math.BigDecimal getPrice() { return price; }
    public void setPrice(java.math.BigDecimal price) { this.price = price; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
