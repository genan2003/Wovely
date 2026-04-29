package com.wovely.wovely.controllers;

import com.wovely.wovely.models.EOrderStatus;
import com.wovely.wovely.models.ManualOrderRequest;
import com.wovely.wovely.models.Order;
import com.wovely.wovely.payload.response.OrderDTO;
import com.wovely.wovely.services.OrderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for seller-specific order operations.
 * Handles manual order creation for off-platform sales.
 */
@RestController
@RequestMapping("/api/seller")
@CrossOrigin(origins = "http://localhost:4200")
public class SellerOrderController {

    @Autowired
    private OrderService orderService;

    /**
     * Create a manual order for off-platform sales.
     * Use this when a sale is made at a craft fair or in-person.
     */
    @PostMapping("/orders/manual")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<?> createManualOrder(@Valid @RequestBody ManualOrderRequest request) {
        try {
            OrderDTO order = orderService.createManualOrder(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Manual order created successfully",
                "order", order,
                "orderNumber", order.getOrderNumber()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get orders by seller ID.
     */
    @GetMapping("/orders/seller/{sellerId}")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<?> getOrdersBySellerId(@PathVariable String sellerId) {
        try {
            List<OrderDTO> orders = orderService.getOrdersBySellerId(sellerId);
            return ResponseEntity.ok(Map.of(
                "orders", orders,
                "totalOrders", orders.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update order details (variations or shipping address) during pre-processing.
     * Locked once order is in PROCESSING status or beyond.
     */
    @PutMapping("/orders/{orderId}/edit")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<?> updateOrder(@PathVariable String orderId, @RequestBody Order updatedOrder) {
        try {
            OrderDTO order = orderService.updateOrderPreProcessing(orderId, updatedOrder);
            if (order != null) {
                return ResponseEntity.ok(Map.of(
                    "message", "Order updated successfully",
                    "order", order
                ));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An unexpected error occurred: " + e.getMessage()));
        }
    }

    /**
     * Update order status (e.g., to PROCESSING, SHIPPED, etc.).
     * This may trigger auto-restocking if status is changed to CANCELLED or REFUNDED.
     */
    @PatchMapping("/orders/{orderId}/status")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<?> updateOrderStatus(@PathVariable String orderId, @RequestBody Map<String, String> request) {
        try {
            String statusStr = request.get("status");
            if (statusStr == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Status is required"));
            }
            
            EOrderStatus newStatus = EOrderStatus.valueOf(statusStr.toUpperCase());
            String reason = request.get("reason");
            
            OrderDTO order = orderService.updateOrderStatus(orderId, newStatus, reason, null);
            if (order != null) {
                return ResponseEntity.ok(Map.of(
                    "message", "Order status updated successfully",
                    "order", order
                ));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid status value"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Generate an automated eco-shipping label.
     * Selects best low-CO2 carrier for the route.
     */
    @PostMapping("/orders/{orderId}/eco-label")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<?> generateEcoLabel(@PathVariable String orderId) {
        try {
            OrderDTO order = orderService.generateEcoShippingLabel(orderId);
            if (order != null) {
                return ResponseEntity.ok(Map.of(
                    "message", "Eco-shipping label generated successfully",
                    "order", order,
                    "label", order.getEcoShippingLabel()
                ));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
}
