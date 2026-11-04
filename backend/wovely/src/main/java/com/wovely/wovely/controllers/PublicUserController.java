package com.wovely.wovely.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.wovely.wovely.models.User;
import com.wovely.wovely.repository.UserRepository;
import java.util.HashMap;
import java.util.Map;

import com.wovely.wovely.models.SellerReview;
import com.wovely.wovely.repository.SellerReviewRepository;
import java.util.List;

@RestController
@RequestMapping("/api/public/users")
public class PublicUserController {

    @Autowired
    UserRepository userRepository;

    @Autowired
    SellerReviewRepository sellerReviewRepository;

    @Autowired
    com.wovely.wovely.services.PurchaseVerificationService purchaseVerification;

    @GetMapping("/seller/{id}")
    public ResponseEntity<?> getSellerProfile(@PathVariable String id) {
        return userRepository.findById(id)
            .map(user -> {
                Map<String, Object> profile = new HashMap<>();
                profile.put("id", user.getId());
                profile.put("username", user.getUsername());
                profile.put("fullName", user.getFullName());
                profile.put("makerStory", user.getMakerStory());
                profile.put("workshopImageUrl", user.getWorkshopImageUrl());
                profile.put("city", user.getCity());
                profile.put("region", user.getRegion());
                return ResponseEntity.ok(profile);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/seller/{id}/reviews")
    public ResponseEntity<List<SellerReview>> getSellerReviews(@PathVariable String id) {
        return ResponseEntity.ok(sellerReviewRepository.findBySellerId(id));
    }

    /**
     * Review a seller as the authenticated buyer (GETs under /api/public are anonymous, this POST is not).
     * Buyer id/name come from the token. {@code verifiedPurchase} is computed from the buyer's received orders (PLAN.md 3.3).
     */
    @PostMapping("/seller/{id}/reviews")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> postSellerReview(@PathVariable String id, @RequestBody SellerReview review,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            com.wovely.wovely.security.services.UserDetailsImpl me) {
        if (!userRepository.existsById(id)) {
            throw com.wovely.common.error.ApiException.notFound("Seller not found");
        }
        if (me.getId().equals(id)) {
            throw com.wovely.common.error.ApiException.badRequest("You cannot review yourself");
        }
        for (int r : new int[] { review.getOverallRating(), review.getCommunicationRating(), review.getShippingRating() }) {
            if (r < 1 || r > 5) {
                throw com.wovely.common.error.ApiException.badRequest("ratings must be between 1 and 5");
            }
        }
        SellerReview toSave = new SellerReview(id, me.getId(), me.getUsername(),
                review.getOverallRating(), review.getCommunicationRating(), review.getShippingRating(),
                review.getComment());
        // Client-sent verifiedPurchase is ignored — only a received order counts.
        toSave.setVerifiedPurchase(purchaseVerification.hasReceivedFromSeller(me.getId(), id));
        return ResponseEntity.ok(sellerReviewRepository.save(toSave));
    }
}
