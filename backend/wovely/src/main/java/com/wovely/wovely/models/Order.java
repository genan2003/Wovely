package com.wovely.wovely.models;

import java.util.Date;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "orders")
@org.springframework.data.mongodb.core.index.CompoundIndex(name = "buyer", def = "{'buyer_id': 1}")
@org.springframework.data.mongodb.core.index.CompoundIndex(name = "seller", def = "{'seller_id': 1}")
public class Order {
    @Id
    private String id;

    @Field("order_number")
    private String orderNumber;

    @Field("buyer_id")
    private String buyerId;

    @Field("buyer_name")
    private String buyerName;

    @Field("seller_id")
    private String sellerId;

    @Field("seller_name")
    private String sellerName;

    @Field("items")
    private OrderItem[] items;

    @Field("total_amount")
    private Double totalAmount;

    @Field("status")
    private EOrderStatus status;

    @Field("created_at")
    private Date createdAt;

    @Field("updated_at")
    private Date updatedAt;

    @Field("shipping_address")
    private String shippingAddress;

    @Field("tracking_number")
    private String trackingNumber;

    @Field("refund_reason")
    private String refundReason;

    @Field("cancellation_reason")
    private String cancellationReason;

    @Field("admin_notes")
    private String adminNotes;

    @Field("is_disputed")
    private Boolean isDisputed;

    @Field("dispute_reason")
    private String disputeReason;

    @Field("eco_shipping_label")
    private String ecoShippingLabel;

    @Field("estimated_delivery_days")
    private Integer estimatedDeliveryDays;

    @Field("is_reviewed")
    private boolean isReviewed = false;

    @Field("refunded_amount")
    private Double refundedAmount;

    // "Ship with Platform" label (PLAN.md 3.6)
    private String carrierName;
    private String carrierMethod;
    private Integer shippingDistanceKm;
    private Double estimatedCo2Kg;
    private Date labelGeneratedAt;

    // Mock wallet escrow (PLAN.md 3.7); null paidCents = not paid through the wallet (legacy/manual orders)
    private Long paidCents;
    private Long walletRefundedCents;
    private Long payoutCents;

    public Order() {
        this.createdAt = new Date();
        this.updatedAt = new Date();
        this.isDisputed = false;
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

    public OrderItem[] getItems() {
        return items;
    }

    public void setItems(OrderItem[] items) {
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

    public Integer getEstimatedDeliveryDays() {
        return estimatedDeliveryDays;
    }

    public void setEstimatedDeliveryDays(Integer estimatedDeliveryDays) {
        this.estimatedDeliveryDays = estimatedDeliveryDays;
    }

    public boolean isReviewed() {
        return isReviewed;
    }

    public void setReviewed(boolean reviewed) {
        isReviewed = reviewed;
    }

    public Double getRefundedAmount() {
        return refundedAmount;
    }

    public void setRefundedAmount(Double refundedAmount) {
        this.refundedAmount = refundedAmount;
    }

    public String getCarrierName() { return carrierName; }
    public void setCarrierName(String carrierName) { this.carrierName = carrierName; }
    public String getCarrierMethod() { return carrierMethod; }
    public void setCarrierMethod(String carrierMethod) { this.carrierMethod = carrierMethod; }
    public Integer getShippingDistanceKm() { return shippingDistanceKm; }
    public void setShippingDistanceKm(Integer shippingDistanceKm) { this.shippingDistanceKm = shippingDistanceKm; }
    public Double getEstimatedCo2Kg() { return estimatedCo2Kg; }
    public void setEstimatedCo2Kg(Double estimatedCo2Kg) { this.estimatedCo2Kg = estimatedCo2Kg; }
    public Date getLabelGeneratedAt() { return labelGeneratedAt; }
    public void setLabelGeneratedAt(Date labelGeneratedAt) { this.labelGeneratedAt = labelGeneratedAt; }

    public Long getPaidCents() { return paidCents; }
    public void setPaidCents(Long paidCents) { this.paidCents = paidCents; }
    public Long getWalletRefundedCents() { return walletRefundedCents; }
    public void setWalletRefundedCents(Long walletRefundedCents) { this.walletRefundedCents = walletRefundedCents; }
    public Long getPayoutCents() { return payoutCents; }
    public void setPayoutCents(Long payoutCents) { this.payoutCents = payoutCents; }
}
