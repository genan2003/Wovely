package com.wovely.inventory.repository;

import com.wovely.inventory.models.Inventory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface InventoryRepository extends MongoRepository<Inventory, String> {
    
    Optional<Inventory> findBySellerId(String sellerId);
    
    boolean existsBySellerId(String sellerId);
}
