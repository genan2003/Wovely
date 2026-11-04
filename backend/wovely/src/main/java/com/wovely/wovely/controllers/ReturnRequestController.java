package com.wovely.wovely.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wovely.wovely.models.ReturnRequest;
import com.wovely.wovely.repository.ReturnRequestRepository;
import com.wovely.wovely.services.ReturnService;

/**
 * Return requests — logic in {@link ReturnService} (ownership, lifecycle, restock and refund).
 */
@RestController
@RequestMapping("/api/returns")
public class ReturnRequestController {

    @Autowired
    ReturnRequestRepository returnRequestRepository;

    @Autowired
    ReturnService returnService;

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'ADMIN')")
    public ReturnRequest createReturnRequest(@RequestBody ReturnRequest request) {
        return returnService.create(request);
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

    /** Seller of the order (or admin) moves the return along; see EReturnStatus for the lifecycle. */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ReturnRequest updateReturnStatus(@PathVariable String id, @RequestBody Map<String, String> data) {
        return returnService.updateStatus(id, data.get("status"));
    }
}
