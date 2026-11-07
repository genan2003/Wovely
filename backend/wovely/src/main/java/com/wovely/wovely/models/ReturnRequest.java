package com.wovely.wovely.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "return_requests")
public class ReturnRequest {
    @Id
    private String id;
    private String orderId;
    private String orderNumber;
    private String buyerId;
    private String sellerId;
    private String sellerName;
    private List<OrderItem> items;
    private String reason;
    private String status; // PICKED_UP, ARRIVED, INSPECTION, REFUNDED
    /** Item prices being returned, in RON cents (PLAN.md 7.5). */
    private Long totalCents;
    private LocalDateTime createdAt;

    public ReturnRequest() {
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public String getBuyerId() { return buyerId; }
    public void setBuyerId(String buyerId) { this.buyerId = buyerId; }
    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
    public String getSellerName() { return sellerName; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }
    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    /** Decimal view for JSON (scale 2); derived, never stored. */
    @org.springframework.data.annotation.Transient
    @com.fasterxml.jackson.annotation.JsonProperty(value = "totalAmount", access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public java.math.BigDecimal getTotalAmount() { return com.wovely.common.money.Money.toDecimal(totalCents); }
    public Long getTotalCents() { return totalCents; }
    public void setTotalCents(Long totalCents) { this.totalCents = totalCents; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
