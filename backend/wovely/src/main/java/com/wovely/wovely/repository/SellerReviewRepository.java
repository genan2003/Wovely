package com.wovely.wovely.repository;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import com.wovely.wovely.models.SellerReview;

public interface SellerReviewRepository extends MongoRepository<SellerReview, String> {
    List<SellerReview> findBySellerId(String sellerId);
}
