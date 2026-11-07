package com.wovely.inventory.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Date;

/**
 * Represents an individual inventory item with visual product information.
 * SKU-free design: sellers identify items by image and name.
 */
@Document(collection = "inventory_items")
@org.springframework.data.mongodb.core.index.CompoundIndex(name = "product_seller", def = "{'productId': 1, 'sellerId': 1}")
public class InventoryItem {

    @Id
    private String id;

    @NotBlank(message = "Product ID is required")
    private String productId;

    @NotBlank(message = "Seller ID is required")
    private String sellerId;

    @NotBlank(message = "Product name is required")
    private String productName;

    private String imageUrl;
    private String category;
    /** Listing price in RON cents, copied from products for the seller views (PLAN.md 7.5). */
    private Long priceCents;

    @NotNull(message = "Stock quantity is required")
    @Min(value = 0, message = "Stock quantity cannot be negative")
    private Integer stockQuantity;

    @Min(value = 0, message = "Low stock threshold must be non-negative")
    private Integer lowStockThreshold;

    private String co2EmissionScore;
    private String shippingMethod;
    private Boolean isHandmade;

    private Boolean isActive;
    private Date createdAt;
    private Date updatedAt;
    private Date lastRestockedAt;

    /** Grows by one on every stock change; consumers ignore events older than what they hold (PLAN.md 4.2). */
    private Long version;
    /** Set in the same atomic update as the change; cleared by StockEventRelay once published. */
    @org.springframework.data.mongodb.core.index.Indexed(sparse = true)
    private Boolean outboxPending;

    public InventoryItem() {
        this.isActive = true;
        this.lowStockThreshold = 5; // Default threshold
        this.createdAt = new Date();
        this.updatedAt = new Date();
    }

    public InventoryItem(String productId, String sellerId, String productName, String imageUrl, 
                         String category, Long priceCents, Integer stockQuantity) {
        this();
        this.productId = productId;
        this.sellerId = sellerId;
        this.productName = productName;
        this.imageUrl = imageUrl;
        this.category = category;
        this.priceCents = priceCents;
        this.stockQuantity = stockQuantity;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Long getPriceCents() { return priceCents; }
    public void setPriceCents(Long priceCents) { this.priceCents = priceCents; }

    /** Decimal view for JSON (scale 2); derived, never stored. */
    @org.springframework.data.annotation.Transient
    @com.fasterxml.jackson.annotation.JsonProperty(value = "price", access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public java.math.BigDecimal getPrice() { return com.wovely.common.money.Money.toDecimal(priceCents); }

    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }

    public Integer getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(Integer lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; }

    public String getCo2EmissionScore() { return co2EmissionScore; }
    public void setCo2EmissionScore(String co2EmissionScore) { this.co2EmissionScore = co2EmissionScore; }

    public String getShippingMethod() { return shippingMethod; }
    public void setShippingMethod(String shippingMethod) { this.shippingMethod = shippingMethod; }

    public Boolean getIsHandmade() { return isHandmade; }
    public void setIsHandmade(Boolean isHandmade) { this.isHandmade = isHandmade; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    public Date getLastRestockedAt() { return lastRestockedAt; }
    public void setLastRestockedAt(Date lastRestockedAt) { this.lastRestockedAt = lastRestockedAt; }

    /**
     * Check if this item is low on stock.
     */
    public boolean isLowStock() {
        return this.stockQuantity <= this.lowStockThreshold;
    }

    /**
     * Check if this item is out of stock.
     */
    public boolean isOutOfStock() {
        return this.stockQuantity == 0;
    }

    /**
     * Reduce stock by a given quantity.
     */
    public void reduceStock(int quantity) {
        if (this.stockQuantity != null) {
            this.stockQuantity = Math.max(0, this.stockQuantity - quantity);
            this.updatedAt = new Date();
        }
    }

    /**
     * Add stock (restock).
     */
    public void addStock(int quantity) {
        if (this.stockQuantity != null) {
            this.stockQuantity += quantity;
            this.updatedAt = new Date();
            this.lastRestockedAt = new Date();
        }
    }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Boolean getOutboxPending() { return outboxPending; }
    public void setOutboxPending(Boolean outboxPending) { this.outboxPending = outboxPending; }
}
