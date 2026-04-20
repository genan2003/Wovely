package com.wovely.wovely.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Request DTO for creating a manual order (off-platform sale).
 * Allows sellers to record sales made outside the platform (e.g., at craft fairs).
 */
public class ManualOrderRequest {

    @NotBlank(message = "Seller ID is required")
    private String sellerId;

    @NotBlank(message = "Seller name is required")
    private String sellerName;

    @NotBlank(message = "Buyer name is required")
    private String buyerName;

    private String buyerEmail;
    private String buyerPhone;

    @NotBlank(message = "Shipping address is required")
    private String shippingAddress;

    @NotNull(message = "Order items are required")
    @NotEmpty(message = "Order must have at least one item")
    private List<ManualOrderItemRequest> items;

    private String notes;
    private String paymentMethod; // CASH, CARD, BANK_TRANSFER, etc.
    private String source; // CRAFT_FAIR, IN_PERSON, PHONE, CUSTOM

    // Getters and Setters
    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }

    public String getSellerName() { return sellerName; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }

    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }

    public String getBuyerEmail() { return buyerEmail; }
    public void setBuyerEmail(String buyerEmail) { this.buyerEmail = buyerEmail; }

    public String getBuyerPhone() { return buyerPhone; }
    public void setBuyerPhone(String buyerPhone) { this.buyerPhone = buyerPhone; }

    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }

    public List<ManualOrderItemRequest> getItems() { return items; }
    public void setItems(List<ManualOrderItemRequest> items) { this.items = items; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
