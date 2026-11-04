package com.wovely.wovely.payload.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Seller edit of an order before processing (Story 2 "Order Flexibility").
 * {@code items} — if present — is the complete new item list (productId, quantity, optional color/size);
 * omitted products are removed. Prices are never taken from the client.
 */
public class UpdateOrderRequest {

    @Size(max = 500)
    private String shippingAddress;

    @Valid
    private List<CreateOrderRequest.Item> items;

    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }
    public List<CreateOrderRequest.Item> getItems() { return items; }
    public void setItems(List<CreateOrderRequest.Item> items) { this.items = items; }
}
