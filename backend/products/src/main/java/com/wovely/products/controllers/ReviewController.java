package com.wovely.products.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.wovely.products.models.Review;
import com.wovely.products.repository.ReviewRepository;
import com.wovely.products.exception.ApiException;
import com.wovely.products.security.JwtPrincipal;
import com.wovely.products.security.OwnershipGuard;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

  @Autowired
  ReviewRepository reviewRepository;

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
   * One review per user and product (409 otherwise). Verified-purchase flag: PLAN.md 3.3.
   */
  @PostMapping
  public ResponseEntity<Review> createReview(@RequestBody Review review) {
    JwtPrincipal caller = OwnershipGuard.current();
    if (caller == null || caller.userId() == null) {
      throw ApiException.forbidden("Token has no user id; please sign in again");
    }
    if (review.getProductId() == null || review.getProductId().isBlank()) {
      throw ApiException.badRequest("productId is required");
    }
    if (review.getRating() < 1 || review.getRating() > 5) {
      throw ApiException.badRequest("rating must be between 1 and 5");
    }
    if (reviewRepository.existsByProductIdAndUserId(review.getProductId(), caller.userId())) {
      throw ApiException.conflict("You have already reviewed this product");
    }
    Review saved = reviewRepository.save(new Review(
        review.getProductId(),
        caller.userId(),
        caller.username(),
        review.getRating(),
        review.getComment(),
        review.getPhotoUrls()));
    return new ResponseEntity<>(saved, HttpStatus.CREATED);
  }
}
