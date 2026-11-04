package com.wovely.wovely.controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wovely.wovely.exception.ApiException;
import com.wovely.wovely.models.Order;
import com.wovely.wovely.models.OrderItem;
import com.wovely.wovely.models.ReturnRequest;
import com.wovely.wovely.repository.OrderRepository;
import com.wovely.wovely.repository.ReturnRequestRepository;
import com.wovely.wovely.security.OwnershipGuard;

/**
 * Return requests. Only the order's buyer may open one; buyer/seller ids, prices and totals are
 * taken from the order, not the client. Status lifecycle & restock: PLAN.md 2.6.
 */
@RestController
@RequestMapping("/api/returns")
public class ReturnRequestController {

    private static final Set<String> STATUSES = Set.of("PICKED_UP", "ARRIVED", "INSPECTION", "REFUNDED", "REJECTED");

    @Autowired
    ReturnRequestRepository returnRequestRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OwnershipGuard ownership;

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'ADMIN')")
    public ReturnRequest createReturnRequest(@RequestBody ReturnRequest request) {
        if (request.getOrderId() == null) {
            throw ApiException.badRequest("orderId is required");
        }
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> ApiException.badRequest("Original order not found."));
        ownership.requireAnyOf(order.getBuyerId());

        List<String> requested = request.getItems() == null ? List.of()
                : request.getItems().stream().map(OrderItem::getProductId).toList();
        List<OrderItem> returned = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            if (requested.contains(item.getProductId())) {
                if (item.isReturned()) {
                    throw ApiException.badRequest("Return already requested for item: " + item.getProductName());
                }
                item.setReturned(true);
                returned.add(item);
            }
        }
        if (returned.isEmpty()) {
            throw ApiException.badRequest("No valid items selected for return.");
        }

        ReturnRequest toSave = new ReturnRequest();
        toSave.setOrderId(order.getId());
        toSave.setOrderNumber(order.getOrderNumber());
        toSave.setBuyerId(order.getBuyerId());
        toSave.setSellerId(order.getSellerId());
        toSave.setSellerName(order.getSellerName());
        toSave.setItems(returned);
        toSave.setReason(request.getReason());
        toSave.setStatus("PICKED_UP");
        toSave.setTotalAmount(returned.stream().mapToDouble(i -> i.getPrice() * i.getQuantity()).sum());

        orderRepository.save(order);
        return returnRequestRepository.save(toSave);
    }

    @GetMapping("/buyer/{buyerId}")
    @PreAuthorize("@ownership.selfOrAdmin(#buyerId)")
    public List<ReturnRequest> getBuyerReturns(@PathVariable String buyerId) {
        return returnRequestRepository.findByBuyerId(buyerId);
    }

    @GetMapping("/seller/{sellerId}")
    @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
    public List<ReturnRequest> getSellerReturns(@PathVariable String sellerId) {
        return returnRequestRepository.findBySellerId(sellerId);
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ReturnRequest> getAllReturns() {
        return returnRequestRepository.findAll();
    }

    /** Seller of the order (or admin) moves the return along. */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ReturnRequest updateReturnStatus(@PathVariable String id, @RequestBody Map<String, String> data) {
        ReturnRequest req = returnRequestRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Return request not found"));
        ownership.requireAnyOf(req.getSellerId());
        String status = data.get("status");
        if (status == null || !STATUSES.contains(status)) {
            throw ApiException.badRequest("status must be one of " + STATUSES);
        }
        req.setStatus(status);
        return returnRequestRepository.save(req);
    }
}
