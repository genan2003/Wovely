package com.wovely.wovely.payload.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Checkout payload. Only product ids and quantities are trusted from the client — price, name,
 * seller, buyer, total and status are determined server-side (PLAN.md 1.5).
 * Unknown fields (e.g. legacy price/totalAmount/status sent by older clients) are ignored.
 */
public class CreateOrderRequest {

    @NotEmpty(message = "at least one item is required")
    @Valid
    private List<Item> items;

    @Size(max = 500)
    private String shippingAddress;

    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }
    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }

    public static class Item {
        @NotBlank(message = "productId is required")
        private String productId;

        @NotNull(message = "quantity is required")
        @Positive(message = "quantity must be positive")
        private Integer quantity;

        private String color;
        private String size;

        public Item() {
        }

        public Item(String productId, Integer quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        public String getSize() { return size; }
        public void setSize(String size) { this.size = size; }
    }
}
