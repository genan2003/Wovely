package com.wovely.wovely.payload.response;

import java.util.Date;
import java.util.List;
import com.wovely.wovely.models.EOrderStatus;

public class OrderDTO {
    private String id;
    private String orderNumber;
    private String buyerId;
    private String buyerName;
    private String sellerId;
    private String sellerName;
    private List<OrderItemDTO> items;
    private Double totalAmount;
    private EOrderStatus status;
    private Date createdAt;
    private Date updatedAt;
    private String shippingAddress;
    private String trackingNumber;
    private String refundReason;
    private String cancellationReason;
    private String adminNotes;
    private Boolean isDisputed;
    private String disputeReason;
    private String ecoShippingLabel;

    public OrderDTO() {
    }

    public OrderDTO(String id, String orderNumber, String buyerId, String buyerName, String sellerId, 
                    String sellerName, List<OrderItemDTO> items, Double totalAmount, EOrderStatus status,
                    Date createdAt, Date updatedAt, String shippingAddress, String trackingNumber,
                    String refundReason, String cancellationReason, String adminNotes, 
                    Boolean isDisputed, String disputeReason, String ecoShippingLabel) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.buyerId = buyerId;
        this.buyerName = buyerName;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.items = items;
        this.totalAmount = totalAmount;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.shippingAddress = shippingAddress;
        this.trackingNumber = trackingNumber;
        this.refundReason = refundReason;
        this.cancellationReason = cancellationReason;
        this.adminNotes = adminNotes;
        this.isDisputed = isDisputed;
        this.disputeReason = disputeReason;
        this.ecoShippingLabel = ecoShippingLabel;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getBuyerId() {
        return buyerId;
    }

    public void setBuyerId(String buyerId) {
        this.buyerId = buyerId;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    public String getSellerId() {
        return sellerId;
    }

    public void setSellerId(String sellerId) {
        this.sellerId = sellerId;
    }

    public String getSellerName() {
        return sellerName;
    }

    public void setSellerName(String sellerName) {
        this.sellerName = sellerName;
    }

    public List<OrderItemDTO> getItems() {
        return items;
    }

    public void setItems(List<OrderItemDTO> items) {
        this.items = items;
    }

    public Double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public EOrderStatus getStatus() {
        return status;
    }

    public void setStatus(EOrderStatus status) {
        this.status = status;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public String getAdminNotes() {
        return adminNotes;
    }

    public void setAdminNotes(String adminNotes) {
        this.adminNotes = adminNotes;
    }

    public Boolean getIsDisputed() {
        return isDisputed;
    }

    public void setIsDisputed(Boolean isDisputed) {
        this.isDisputed = isDisputed;
    }

    public String getDisputeReason() {
        return disputeReason;
    }

    public void setDisputeReason(String disputeReason) {
        this.disputeReason = disputeReason;
    }

    public String getEcoShippingLabel() {
        return ecoShippingLabel;
    }

    public void setEcoShippingLabel(String ecoShippingLabel) {
        this.ecoShippingLabel = ecoShippingLabel;
    }
}
