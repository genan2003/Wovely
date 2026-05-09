package com.wovely.products.repository;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import com.wovely.products.models.Review;

public interface ReviewRepository extends MongoRepository<Review, String> {
  List<Review> findByProductId(String productId);
  List<Review> findByUserId(String userId);
}
