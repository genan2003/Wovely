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
    private java.math.BigDecimal totalAmount;
    /** Items + delivery in cents (PLAN.md 7.5); {@code totalAmount} is the same amount as a decimal. */
    private Long totalCents;
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
    private Integer estimatedDeliveryDays;
    private boolean isReviewed;
    private java.math.BigDecimal refundedAmount;
    private Long refundedCents;

    public OrderDTO() {
    }

    public OrderDTO(String id, String orderNumber, String buyerId, String buyerName, String sellerId, 
                    String sellerName, List<OrderItemDTO> items, Long totalCents, EOrderStatus status,
                    Date createdAt, Date updatedAt, String shippingAddress, String trackingNumber,
                    String refundReason, String cancellationReason, String adminNotes, 
                    Boolean isDisputed, String disputeReason, String ecoShippingLabel, Integer estimatedDeliveryDays, boolean isReviewed) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.buyerId = buyerId;
        this.buyerName = buyerName;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.items = items;
        this.totalCents = totalCents;
        this.totalAmount = com.wovely.common.money.Money.toDecimal(totalCents);
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
        this.estimatedDeliveryDays = estimatedDeliveryDays;
        this.isReviewed = isReviewed;
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

    public java.math.BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Long getTotalCents() {
        return totalCents;
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

    public java.math.BigDecimal getRefundedAmount() {
        return refundedAmount;
    }

    public Long getRefundedCents() {
        return refundedCents;
    }

    public OrderDTO withRefunded(Long refundedCents) {
        this.refundedCents = refundedCents;
        this.refundedAmount = com.wovely.common.money.Money.toDecimal(refundedCents);
        return this;
    }

    // "Ship with Platform" label (PLAN.md 3.6)
    private String carrierName;
    private String carrierMethod;
    private Integer shippingDistanceKm;
    private Double estimatedCo2Kg;
    private Date labelGeneratedAt;

    public OrderDTO withShipping(String carrierName, String carrierMethod, Integer shippingDistanceKm,
            Double estimatedCo2Kg, Date labelGeneratedAt) {
        this.carrierName = carrierName;
        this.carrierMethod = carrierMethod;
        this.shippingDistanceKm = shippingDistanceKm;
        this.estimatedCo2Kg = estimatedCo2Kg;
        this.labelGeneratedAt = labelGeneratedAt;
        return this;
    }

    public String getCarrierName() { return carrierName; }
    public String getCarrierMethod() { return carrierMethod; }
    public Integer getShippingDistanceKm() { return shippingDistanceKm; }
    public Double getEstimatedCo2Kg() { return estimatedCo2Kg; }
    public Date getLabelGeneratedAt() { return labelGeneratedAt; }

    /** When the order was delivered; starts the return window. */
    private Date deliveredAt;
    /** Last moment a return can be opened (null before delivery), see {@code Order#getReturnDeadline}. */
    private Date returnDeadline;

    public OrderDTO withReturnWindow(Date deliveredAt, Date returnDeadline) {
        this.deliveredAt = deliveredAt;
        this.returnDeadline = returnDeadline;
        return this;
    }

    public Date getDeliveredAt() { return deliveredAt; }
    public Date getReturnDeadline() { return returnDeadline; }

    private com.wovely.wovely.models.EDeliveryOption deliveryOption;
    private java.math.BigDecimal deliveryFee;
    private Long deliveryFeeCents;

    public OrderDTO withDelivery(com.wovely.wovely.models.EDeliveryOption option, Long feeCents) {
        this.deliveryOption = option;
        this.deliveryFeeCents = feeCents;
        this.deliveryFee = com.wovely.common.money.Money.toDecimal(feeCents);
        return this;
    }

    public com.wovely.wovely.models.EDeliveryOption getDeliveryOption() { return deliveryOption; }
    public java.math.BigDecimal getDeliveryFee() { return deliveryFee; }
    public Long getDeliveryFeeCents() { return deliveryFeeCents; }

    /** Whether the seller may still edit items/address (see OrderStatusPolicy#isEditable(Order)). */
    private boolean editable;

    public OrderDTO withEditable(boolean editable) {
        this.editable = editable;
        return this;
    }

    public boolean isEditable() { return editable; }

    /** Status timeline, oldest first (PLAN.md 7.1). */
    private List<OrderTimelineEntryDTO> timeline = List.of();

    public OrderDTO withTimeline(List<OrderTimelineEntryDTO> timeline) {
        this.timeline = timeline == null ? List.of() : List.copyOf(timeline);
        return this;
    }

    public List<OrderTimelineEntryDTO> getTimeline() { return timeline; }
}
