package com.wovely.wovely.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.wovely.wovely.models.OrderItem;
import com.wovely.wovely.models.ReturnRequest;
import com.wovely.wovely.models.Order;
import com.wovely.wovely.repository.ReturnRequestRepository;
import com.wovely.wovely.repository.OrderRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@CrossOrigin(origins = "http://localhost:4200", maxAge = 3600)
@RestController
@RequestMapping("/api/returns")
public class ReturnRequestController {

    @Autowired
    ReturnRequestRepository returnRequestRepository;

    @Autowired
    OrderRepository orderRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'ADMIN')")
    public ResponseEntity<?> createReturnRequest(@RequestBody ReturnRequest request) {
        // 1. Find the original order
        Optional<Order> orderOpt = orderRepository.findById(request.getOrderId());
        if (orderOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "Original order not found."));
        }

        Order order = orderOpt.get();
        List<String> requestProductIds = request.getItems().stream()
            .map(OrderItem::getProductId)
            .collect(Collectors.toList());

        // 2. Mark items as returned in the order and check for duplicates
        boolean updated = false;
        for (OrderItem item : order.getItems()) {
            if (requestProductIds.contains(item.getProductId())) {
                if (item.isReturned()) {
                    return ResponseEntity.badRequest().body(java.util.Map.of("message", "Return already requested for item: " + item.getProductName()));
                }
                item.setReturned(true);
                updated = true;
            }
        }

        if (!updated) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "No valid items selected for return."));
        }

        // 3. Save order and return request
        orderRepository.save(order);
        ReturnRequest saved = returnRequestRepository.save(request);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/buyer/{buyerId}")
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'ADMIN')")
    public ResponseEntity<List<ReturnRequest>> getBuyerReturns(@PathVariable String buyerId) {
        return ResponseEntity.ok(returnRequestRepository.findByBuyerId(buyerId));
    }

    @GetMapping("/seller/{sellerId}")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<List<ReturnRequest>> getSellerReturns(@PathVariable String sellerId) {
        return ResponseEntity.ok(returnRequestRepository.findBySellerId(sellerId));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ReturnRequest>> getAllReturns() {
        return ResponseEntity.ok(returnRequestRepository.findAll());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<?> updateReturnStatus(@PathVariable String id, @RequestBody java.util.Map<String, String> data) {
        return returnRequestRepository.findById(id)
            .map(req -> {
                req.setStatus(data.get("status"));
                returnRequestRepository.save(req);
                return ResponseEntity.ok(req);
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
