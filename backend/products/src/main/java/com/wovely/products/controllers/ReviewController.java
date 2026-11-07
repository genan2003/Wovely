package com.wovely.products.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.wovely.products.models.Review;
import com.wovely.products.repository.ReviewRepository;
import com.wovely.common.error.ApiException;
import com.wovely.common.security.JwtPrincipal;
import com.wovely.common.security.OwnershipGuard;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

  @Autowired
  ReviewRepository reviewRepository;

  @Autowired
  com.wovely.products.clients.PurchaseVerificationClient purchaseVerification;

  @GetMapping("/product/{productId}")
  public ResponseEntity<?> getProductReviews(@PathVariable String productId) {
    try {
      List<Review> reviews = reviewRepository.findByProductId(productId);
      
      double averageRating = reviews.stream()
          .mapToInt(Review::getRating)
          .average()
          .orElse(0.0);

      Map<String, Object> response = new HashMap<>();
      response.put("reviews", reviews);
      response.put("averageRating", averageRating);
      response.put("totalReviews", reviews.size());

      return ResponseEntity.ok(response);
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Create a review as the authenticated caller. {@code userId}/{@code username} from the body are ignored.
   * One review per user and product (409 otherwise). {@code verifiedPurchase} is asked from core and
   * never taken from the body (PLAN.md 3.3).
   */
  @PostMapping
  public ResponseEntity<Review> createReview(@jakarta.validation.Valid @RequestBody com.wovely.products.payload.ReviewRequest review) {
    JwtPrincipal caller = OwnershipGuard.current();
    if (caller == null || caller.userId() == null) {
      throw ApiException.forbidden("Token has no user id; please sign in again");
    }
    if (reviewRepository.existsByProductIdAndUserId(review.productId(), caller.userId())) {
      throw ApiException.conflict("You have already reviewed this product");
    }
    Review toSave = new Review(
        review.productId(),
        caller.userId(),
        caller.username(),
        review.rating(),
        review.comment(),
        review.photoUrls() == null ? List.of() : List.copyOf(review.photoUrls()));
    toSave.setVerifiedPurchase(purchaseVerification.hasReceived(caller.userId(), review.productId()));
    return new ResponseEntity<>(reviewRepository.save(toSave), HttpStatus.CREATED);
  }
}
