package com.wovely.inventory.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Date;

/**
 * Represents a seller's inventory collection.
 * Each seller has one inventory containing multiple inventory items.
 */
@Document(collection = "inventories")
public class Inventory {

    @Id
    private String id;

    private String sellerId;
    private String sellerName;
    private Date createdAt;
    private Date updatedAt;
    private int totalItems;
    private int lowStockItems;

    public Inventory() {
        this.createdAt = new Date();
        this.updatedAt = new Date();
    }

    public Inventory(String sellerId, String sellerName) {
        this();
        this.sellerId = sellerId;
        this.sellerName = sellerName;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }

    public String getSellerName() { return sellerName; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    public int getTotalItems() { return totalItems; }
    public void setTotalItems(int totalItems) { this.totalItems = totalItems; }

    public int getLowStockItems() { return lowStockItems; }
    public void setLowStockItems(int lowStockItems) { this.lowStockItems = lowStockItems; }
}
