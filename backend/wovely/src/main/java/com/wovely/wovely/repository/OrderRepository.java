package com.wovely.wovely.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.wovely.wovely.models.Order;
import com.wovely.wovely.models.EOrderStatus;

@Repository
public interface OrderRepository extends MongoRepository<Order, String> {
    Optional<Order> findByOrderNumber(String orderNumber);
    
    List<Order> findByBuyerId(String buyerId);
    
    List<Order> findBySellerId(String sellerId);
    
    List<Order> findByBuyerNameContainingIgnoreCase(String buyerName);
    
    List<Order> findBySellerNameContainingIgnoreCase(String sellerName);
    
    List<Order> findByStatus(EOrderStatus status);
    
    List<Order> findByIsDisputedTrue();
    
    List<Order> findByBuyerNameContainingIgnoreCaseOrSellerNameContainingIgnoreCaseOrOrderNumberContaining(
        String buyerName, String sellerName, String orderNumber);
}
