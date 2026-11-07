package com.wovely.wovely.models;

import org.springframework.data.mongodb.core.mapping.Field;

public class OrderItem {
    private String productId;
    private String productName;
    private Integer quantity;
    /** Unit price in RON cents at the time of purchase (PLAN.md 7.5). */
    private Long priceCents;
    private String imageUrl;
    private String color;
    private String size;

    @Field("is_returned")
    private boolean isReturned = false;

    @Field("is_reviewed")
    private boolean isReviewed = false;

    public OrderItem() {
    }

    public OrderItem(String productId, String productName, Integer quantity, Long priceCents, String imageUrl) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.priceCents = priceCents;
        this.imageUrl = imageUrl;
    }

    public OrderItem(String productId, String productName, Integer quantity, Long priceCents, String imageUrl, String color, String size) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.priceCents = priceCents;
        this.imageUrl = imageUrl;
        this.color = color;
        this.size = size;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Long getPriceCents() {
        return priceCents;
    }

    public void setPriceCents(Long priceCents) {
        this.priceCents = priceCents;
    }

    /** Decimal view for JSON (scale 2); derived, never stored. */
    @org.springframework.data.annotation.Transient
    @com.fasterxml.jackson.annotation.JsonProperty(value = "price", access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public java.math.BigDecimal getPrice() {
        return com.wovely.common.money.Money.toDecimal(priceCents);
    }

    /** Unit price × quantity in cents. */
    public long lineTotalCents() {
        return com.wovely.common.money.Money.times(priceCents == null ? 0 : priceCents, quantity == null ? 0 : quantity);
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public boolean isReturned() {
        return isReturned;
    }

    public void setReturned(boolean returned) {
        isReturned = returned;
    }

    public boolean isReviewed() {
        return isReviewed;
    }

    public void setReviewed(boolean reviewed) {
        isReviewed = reviewed;
    }
}
