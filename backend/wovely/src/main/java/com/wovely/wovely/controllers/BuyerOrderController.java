package com.wovely.wovely.controllers;

import com.wovely.wovely.models.Order;
import com.wovely.wovely.models.OrderItem;
import com.wovely.wovely.repository.OrderRepository;
import com.wovely.wovely.payload.response.OrderDTO;
import com.wovely.wovely.services.OrderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for buyer-specific order operations.
 * Handles order creation during checkout.
 */
@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "http://localhost:4200")
public class BuyerOrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    /**
     * Create a new order (Checkout).
     * This triggers real-time stock deduction.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'ADMIN')")
    public ResponseEntity<?> createOrder(@Valid @RequestBody Order order) {
        try {
            OrderDTO savedOrder = orderService.createOrder(order);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedOrder);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An unexpected error occurred: " + e.getMessage()));
        }
    }

    /**
     * Cancel an order.
     * This triggers auto-restocking of items.
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> cancelOrder(@PathVariable String id, @RequestBody Map<String, String> request) {
        try {
            String reason = request.getOrDefault("reason", "Buyer cancelled");
            OrderDTO updatedOrder = orderService.updateOrderStatus(id, com.wovely.wovely.models.EOrderStatus.CANCELLED, reason, null);
            if (updatedOrder != null) {
                return ResponseEntity.ok(updatedOrder);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Confirm delivery of an order.
     * Transitions order to DELIVERED status.
     */
    @PostMapping("/{id}/confirm-receipt")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> confirmReceipt(@PathVariable String id) {
        try {
            OrderDTO updatedOrder = orderService.updateOrderStatus(id, com.wovely.wovely.models.EOrderStatus.DELIVERED, "Buyer confirmed receipt", null);
            if (updatedOrder != null) {
                return ResponseEntity.ok(updatedOrder);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Mark specific items in an order as reviewed.
     */
    @PostMapping("/{id}/mark-reviewed")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> markReviewed(@PathVariable String id, @RequestBody List<String> productIds) {
        try {
            Optional<Order> orderOpt = orderRepository.findById(id);
            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();
                for (OrderItem item : order.getItems()) {
                    if (productIds.contains(item.getProductId())) {
                        item.setReviewed(true);
                    }
                }
                order.setReviewed(true);
                orderRepository.save(order);
                return ResponseEntity.ok(Map.of("message", "Items marked as reviewed"));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get orders for the current buyer.
     */
    @GetMapping("/buyer/{buyerId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> getOrdersByBuyerId(@PathVariable String buyerId) {
        try {
            List<OrderDTO> orders = orderService.getOrdersByBuyerId(buyerId);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get a specific order by ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'ADMIN')")
    public ResponseEntity<?> getOrderById(@PathVariable String id) {
        try {
            OrderDTO order = orderService.getOrderById(id);
            if (order != null) {
                return ResponseEntity.ok(order);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
}
