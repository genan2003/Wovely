package com.wovely.inventory.repository;

import com.wovely.inventory.models.InventoryItem;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryItemRepository extends MongoRepository<InventoryItem, String> {
    
    List<InventoryItem> findBySellerId(String sellerId);
    
    List<InventoryItem> findBySellerIdAndIsActive(String sellerId, Boolean isActive);
    
    Optional<InventoryItem> findBySellerIdAndProductId(String sellerId, String productId);
    
}
