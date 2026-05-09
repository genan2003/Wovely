package com.wovely.products.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.wovely.products.models.Review;
import com.wovely.products.repository.ReviewRepository;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@CrossOrigin(origins = "http://localhost:4200", maxAge = 3600)
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

  @PostMapping
  public ResponseEntity<Review> createReview(@RequestBody Review review) {
    try {
      Review _review = reviewRepository.save(new Review(
          review.getProductId(),
          review.getUserId(),
          review.getUsername(),
          review.getRating(),
          review.getComment(),
          review.getPhotoUrls()
      ));
      return new ResponseEntity<>(_review, HttpStatus.CREATED);
    } catch (Exception e) {
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
