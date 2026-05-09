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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:4200", maxAge = 3600)
@RestController
@RequestMapping("/api/public/users")
public class PublicUserController {

    @Autowired
    UserRepository userRepository;

    @Autowired
    SellerReviewRepository sellerReviewRepository;

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

    @PostMapping("/seller/{id}/reviews")
    public ResponseEntity<?> postSellerReview(@PathVariable String id, @RequestBody SellerReview review) {
        review.setSellerId(id);
        SellerReview saved = sellerReviewRepository.save(review);
        return ResponseEntity.ok(saved);
    }
}
