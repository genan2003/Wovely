package com.wovely.wovely.payload.response;

public class OrderItemDTO {
    private String productId;
    private String productName;
    private Integer quantity;
    private java.math.BigDecimal price;
    /** Unit price in cents (PLAN.md 7.5); {@code price} is the same amount as a decimal. */
    private Long priceCents;
    private String imageUrl;
    private boolean isReturned;
    private boolean isReviewed;

    public OrderItemDTO() {
    }

    public OrderItemDTO(String productId, String productName, Integer quantity, Long priceCents, String imageUrl) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.priceCents = priceCents;
        this.price = com.wovely.common.money.Money.toDecimal(priceCents);
        this.imageUrl = imageUrl;
    }

    public OrderItemDTO(String productId, String productName, Integer quantity, Long priceCents, String imageUrl, boolean isReturned, boolean isReviewed) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.priceCents = priceCents;
        this.price = com.wovely.common.money.Money.toDecimal(priceCents);
        this.imageUrl = imageUrl;
        this.isReturned = isReturned;
        this.isReviewed = isReviewed;
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

    public java.math.BigDecimal getPrice() {
        return price;
    }

    public Long getPriceCents() {
        return priceCents;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
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
