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

    /** Items + delivery in RON cents (PLAN.md 7.5); always set through {@link #recomputeTotal()}. */
    @Field("total_cents")
    private Long totalCents;

    @Field("status")
    private EOrderStatus status;

    @Field("created_at")
    private Date createdAt;

    /** Every status change in order (PLAN.md 7.1); empty for orders created before the timeline existed. */
    @Field("status_history")
    private java.util.List<OrderStatusChange> statusHistory = new java.util.ArrayList<>();

    @Field("updated_at")
    private Date updatedAt;

    /** Delivery chosen at checkout and its fee in RON (included in totalAmount); null/0 for manual sales. */
    private EDeliveryOption deliveryOption;
    private Long deliveryFeeCents;

    /** When the order reached DELIVERED (set by OrderService#transition); starts the return window. */
    private Date deliveredAt;

    /** Buyers may open a return for this many days after delivery (enforced in ReturnService). */
    public static final int RETURN_WINDOW_DAYS = 30;

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

    /** Item prices refunded through returns, in cents. */
    @Field("refunded_cents")
    private Long refundedCents;

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

    public Long getTotalCents() {
        return totalCents;
    }

    public void setTotalCents(Long totalCents) {
        this.totalCents = totalCents;
    }

    @org.springframework.data.annotation.Transient
    public java.math.BigDecimal getTotalAmount() {
        return com.wovely.common.money.Money.toDecimal(totalCents);
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

    public Long getRefundedCents() {
        return refundedCents;
    }

    public void setRefundedCents(Long refundedCents) {
        this.refundedCents = refundedCents;
    }

    @org.springframework.data.annotation.Transient
    public java.math.BigDecimal getRefundedAmount() {
        return com.wovely.common.money.Money.toDecimal(refundedCents);
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

    public Date getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Date deliveredAt) { this.deliveredAt = deliveredAt; }

    /**
     * Last moment a return can be opened, or null before delivery. Orders delivered before
     * {@code deliveredAt} existed fall back to {@code createdAt} (the stricter choice).
     * Read-only in JSON; not stored (Spring Data maps fields, not getters).
     */
    public Date getReturnDeadline() {
        if (status != EOrderStatus.DELIVERED && status != EOrderStatus.COMPLETED) {
            return null;
        }
        Date start = deliveredAt != null ? deliveredAt : createdAt;
        return start == null ? null
                : Date.from(start.toInstant().plus(java.time.Duration.ofDays(RETURN_WINDOW_DAYS)));
    }

    public EDeliveryOption getDeliveryOption() { return deliveryOption; }
    public void setDeliveryOption(EDeliveryOption deliveryOption) { this.deliveryOption = deliveryOption; }
    public Long getDeliveryFeeCents() { return deliveryFeeCents; }
    public void setDeliveryFeeCents(Long deliveryFeeCents) { this.deliveryFeeCents = deliveryFeeCents; }
    @org.springframework.data.annotation.Transient
    public java.math.BigDecimal getDeliveryFee() { return com.wovely.common.money.Money.toDecimal(deliveryFeeCents); }

    /** Sum of item prices × quantities in cents (excludes delivery). */
    public long itemsTotalCents() {
        long sum = 0;
        if (this.items != null) {
            for (OrderItem i : this.items) {
                sum = Math.addExact(sum, i.lineTotalCents());
            }
        }
        return sum;
    }

    /** Items plus delivery, in cents: the one place the order total is computed. */
    public void recomputeTotal() {
        this.totalCents = Math.addExact(itemsTotalCents(), deliveryFeeCents == null ? 0 : deliveryFeeCents);
    }

    public java.util.List<OrderStatusChange> getStatusHistory() {
        return statusHistory;
    }

    public void setStatusHistory(java.util.List<OrderStatusChange> statusHistory) {
        this.statusHistory = statusHistory == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(statusHistory);
    }

    /**
     * Appends a timeline entry; callers set the status itself (only OrderService does both). The first change of an
     * order created before the timeline existed first stores the steps derived from its dates (lazy backfill), so
     * the earlier history isn't lost once real entries start.
     */
    public void recordStatusChange(OrderStatusChange change) {
        if (statusHistory == null) {
            statusHistory = new java.util.ArrayList<>();
        }
        if (statusHistory.isEmpty() && change.from() != null) {
            statusHistory.addAll(derivedTimeline(change.from()));
        }
        statusHistory.add(change);
    }

    /**
     * The recorded history, or — for orders created before it existed — the steps that can be read from stored
     * dates: created (PENDING), delivered ({@code deliveredAt}) and the current status ({@code updatedAt}).
     * Derived entries have {@code actorRole == null}. Reading never writes; see {@link #recordStatusChange}.
     */
    public java.util.List<OrderStatusChange> timeline() {
        if (statusHistory != null && !statusHistory.isEmpty()) {
            return java.util.List.copyOf(statusHistory);
        }
        return derivedTimeline(status);
    }

    private java.util.List<OrderStatusChange> derivedTimeline(EOrderStatus current) {
        java.util.List<OrderStatusChange> derived = new java.util.ArrayList<>();
        EOrderStatus last = null;
        if (createdAt != null) {
            derived.add(new OrderStatusChange(null, EOrderStatus.PENDING, createdAt, null, null, null));
            last = EOrderStatus.PENDING;
        }
        if (deliveredAt != null && current != EOrderStatus.DELIVERED) {
            derived.add(new OrderStatusChange(last, EOrderStatus.DELIVERED, deliveredAt, null, null, null));
            last = EOrderStatus.DELIVERED;
        }
        if (current != null && current != last) {
            Date when = current == EOrderStatus.DELIVERED && deliveredAt != null ? deliveredAt
                    : (updatedAt != null ? updatedAt : createdAt);
            derived.add(new OrderStatusChange(last, current, when, null, null, reasonFor(current)));
        }
        return derived;
    }

    private String reasonFor(EOrderStatus s) {
        return s == EOrderStatus.CANCELLED ? cancellationReason : s == EOrderStatus.REFUNDED ? refundReason : null;
    }
}
