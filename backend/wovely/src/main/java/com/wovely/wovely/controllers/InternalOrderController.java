package com.wovely.wovely.controllers;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wovely.wovely.services.PurchaseVerificationService;

/** Order facts other services need (internal API key only). */
@RestController
@RequestMapping("/internal/orders")
public class InternalOrderController {

    private final PurchaseVerificationService verification;

    public InternalOrderController(PurchaseVerificationService verification) {
        this.verification = verification;
    }

    /** Used by the products service to flag "Verified Purchase" reviews (PLAN.md 3.3). */
    @GetMapping("/verify-purchase")
    public Map<String, Boolean> verifyPurchase(@RequestParam("buyerId") String buyerId,
            @RequestParam("productId") String productId) {
        return Map.of("verified", verification.hasReceivedProduct(buyerId, productId));
    }
}
