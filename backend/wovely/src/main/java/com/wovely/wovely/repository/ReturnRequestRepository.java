package com.wovely.wovely.repository;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import com.wovely.wovely.models.ReturnRequest;

public interface ReturnRequestRepository extends MongoRepository<ReturnRequest, String> {
    List<ReturnRequest> findByBuyerId(String buyerId);
    List<ReturnRequest> findBySellerId(String sellerId);
}
