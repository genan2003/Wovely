package com.wovely.wovely.controllers;

import com.wovely.wovely.models.ManualOrderRequest;
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
}
