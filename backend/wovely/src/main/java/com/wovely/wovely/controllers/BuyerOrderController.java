package com.wovely.wovely.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wovely.wovely.models.EOrderStatus;
import com.wovely.wovely.models.Order;
import com.wovely.wovely.models.OrderItem;
import com.wovely.wovely.payload.request.CreateOrderRequest;
import com.wovely.wovely.payload.response.OrderDTO;
import com.wovely.wovely.repository.OrderRepository;
import com.wovely.wovely.security.services.UserDetailsImpl;
import com.wovely.wovely.services.OrderService;
import com.wovely.wovely.services.OrderStatusPolicy.Actor;
import com.wovely.wovely.security.OwnershipGuard;

import jakarta.validation.Valid;

/**
 * Buyer order operations. Every action is restricted to the order's buyer (or an admin);
 * reads of a single order also allow its seller.
 */
@RestController
@RequestMapping("/api/orders")
public class BuyerOrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    /** Checkout: prices and totals are computed server-side; triggers real-time stock deduction. */
    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'ADMIN')")
    public ResponseEntity<OrderDTO> createOrder(@Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal UserDetailsImpl buyer) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request, buyer));
    }

    /** Cancel own order; triggers auto-restock. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'ADMIN')")
    public OrderDTO cancelOrder(@PathVariable String id, @RequestBody(required = false) Map<String, String> request) {
        orderService.requireBuyerOrder(id);
        String reason = request != null ? request.getOrDefault("reason", "Buyer cancelled") : "Buyer cancelled";
        return orderService.updateOrderStatus(id, EOrderStatus.CANCELLED, reason, null, actor());
    }

    /** Confirm delivery of own order. */
    @PostMapping("/{id}/confirm-receipt")
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'ADMIN')")
    public OrderDTO confirmReceipt(@PathVariable String id) {
        orderService.requireBuyerOrder(id);
        return orderService.updateOrderStatus(id, EOrderStatus.DELIVERED, "Buyer confirmed receipt", null, actor());
    }

    /** Buyer completes a delivered order (closes it). */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'ADMIN')")
    public OrderDTO complete(@PathVariable String id) {
        orderService.requireBuyerOrder(id);
        return orderService.updateOrderStatus(id, EOrderStatus.COMPLETED, "Buyer completed order", null, actor());
    }

    private static Actor actor() {
        return OwnershipGuard.isAdmin(OwnershipGuard.current()) ? Actor.ADMIN : Actor.BUYER;
    }

    /** Mark items of own order as reviewed. */
    @PostMapping("/{id}/mark-reviewed")
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'ADMIN')")
    public Map<String, String> markReviewed(@PathVariable String id, @RequestBody List<String> productIds) {
        Order order = orderService.requireBuyerOrder(id);
        for (OrderItem item : order.getItems()) {
            if (productIds.contains(item.getProductId())) {
                item.setReviewed(true);
            }
        }
        order.setReviewed(true);
        orderRepository.save(order);
        return Map.of("message", "Items marked as reviewed");
    }

    /** Order history — only your own (admins may read any buyer's). */
    @GetMapping("/buyer/{buyerId}")
    @PreAuthorize("@ownership.selfOrAdmin(#buyerId)")
    public List<OrderDTO> getOrdersByBuyerId(@PathVariable String buyerId) {
        return orderService.getOrdersByBuyerId(buyerId);
    }

    /** A single order — visible to its buyer, its seller or an admin. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'ADMIN')")
    public OrderDTO getOrderById(@PathVariable String id) {
        return orderService.getOrderForParticipant(id);
    }
}
