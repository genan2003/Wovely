package com.wovely.wovely.controllers;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wovely.common.error.ApiException;
import com.wovely.wovely.models.EOrderStatus;
import com.wovely.wovely.models.ManualOrderRequest;
import com.wovely.wovely.payload.request.UpdateOrderRequest;
import com.wovely.wovely.security.OwnershipGuard;
import com.wovely.wovely.services.OrderStatusPolicy.Actor;
import com.wovely.wovely.payload.response.OrderDTO;
import com.wovely.wovely.services.OrderService;

import jakarta.validation.Valid;

/**
 * Seller order operations. A seller may only act on their own orders (admins on any).
 */
@RestController
@RequestMapping("/api/seller")
public class SellerOrderController {

    @Autowired
    private OrderService orderService;

    /** Manual order for off-platform sales (craft fair, in person). Seller-entered prices are accepted. */
    @PostMapping("/orders/manual")
    @PreAuthorize("@ownership.sellerOrAdmin(#request.sellerId)")
    public ResponseEntity<?> createManualOrder(@Valid @RequestBody ManualOrderRequest request) {
        OrderDTO order = orderService.createManualOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "message", "Manual order created successfully",
            "order", order,
            "orderNumber", order.getOrderNumber()));
    }

    @GetMapping("/orders/seller/{sellerId}")
    @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
    public Map<String, Object> getOrdersBySellerId(@PathVariable String sellerId) {
        var orders = orderService.getOrdersBySellerId(sellerId);
        return Map.of("orders", orders, "totalOrders", orders.size());
    }

    /** Edit address/variations while PENDING or CONFIRMED; 409 once locked. */
    @PutMapping("/orders/{orderId}/edit")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public Map<String, Object> updateOrder(@PathVariable String orderId, @Valid @RequestBody UpdateOrderRequest updatedOrder) {
        orderService.requireSellerOrder(orderId);
        OrderDTO order = orderService.updateOrderPreProcessing(orderId, updatedOrder);
        return Map.of("message", "Order updated successfully", "order", order);
    }

    /** Move order through its lifecycle (OrderStatusPolicy, 409 if illegal); cancelling before shipment restocks. */
    @PatchMapping("/orders/{orderId}/status")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public Map<String, Object> updateOrderStatus(@PathVariable String orderId, @RequestBody Map<String, String> request) {
        orderService.requireSellerOrder(orderId);
        String statusStr = request.get("status");
        if (statusStr == null) {
            throw ApiException.badRequest("Status is required");
        }
        EOrderStatus newStatus;
        try {
            newStatus = EOrderStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid status value");
        }
        Integer estimatedDeliveryDays = null;
        String eta = request.get("estimatedDeliveryDays");
        if (eta != null) {
            try {
                estimatedDeliveryDays = Integer.parseInt(eta);
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("estimatedDeliveryDays must be a number");
            }
        }
        OrderDTO order = orderService.updateOrderStatus(orderId, newStatus, request.get("reason"), estimatedDeliveryDays,
                OwnershipGuard.isAdmin(OwnershipGuard.current()) ? Actor.ADMIN : Actor.SELLER);
        return Map.of("message", "Order status updated successfully", "order", order);
    }

    @PostMapping("/orders/{orderId}/eco-label")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public Map<String, Object> generateEcoLabel(@PathVariable String orderId) {
        orderService.requireSellerOrder(orderId);
        OrderDTO order = orderService.generateEcoShippingLabel(orderId, false);
        return Map.of(
            "message", "Eco-shipping label generated successfully",
            "order", order,
            "label", order.getEcoShippingLabel());
    }
}
