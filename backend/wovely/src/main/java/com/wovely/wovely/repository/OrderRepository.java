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

    /** Verified purchase of a product: some received order of this buyer contains it (PLAN.md 3.3). */
    boolean existsByBuyerIdAndStatusInAndItemsProductId(String buyerId, java.util.Collection<EOrderStatus> statuses,
            String productId);

    /** Verified purchase from a seller: this buyer has received at least one order from them. */
    boolean existsByBuyerIdAndSellerIdAndStatusIn(String buyerId, String sellerId,
            java.util.Collection<EOrderStatus> statuses);
    
    List<Order> findByBuyerNameContainingIgnoreCaseOrSellerNameContainingIgnoreCaseOrOrderNumberContaining(
        String buyerName, String sellerName, String orderNumber);
}
