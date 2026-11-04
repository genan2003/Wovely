package com.wovely.inventory.services;

import com.wovely.inventory.models.Inventory;
import com.wovely.inventory.models.InventoryItem;
import com.wovely.inventory.repository.InventoryRepository;
import com.wovely.inventory.repository.InventoryItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
/** Read side of the seller inventory views. All stock writes go through {@link com.wovely.inventory.stock.StockLedger}. */
public class InventoryService {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    /**
     * Get or create inventory for a seller.
     */
    public Inventory getOrCreateInventory(String sellerId, String sellerName) {
        return inventoryRepository.findBySellerId(sellerId)
            .orElseGet(() -> {
                Inventory newInventory = new Inventory(sellerId, sellerName);
                return inventoryRepository.save(newInventory);
            });
    }

    /**
     * Get inventory by seller ID.
     */
    public Optional<Inventory> getInventoryBySellerId(String sellerId) {
        return inventoryRepository.findBySellerId(sellerId);
    }





    /**
     * Get all inventory items for a seller.
     */
    public List<InventoryItem> getInventoryItems(String sellerId) {
        return inventoryItemRepository.findBySellerIdAndIsActive(sellerId, true);
    }

    /**
     * Get inventory items by category.
     */
    public List<InventoryItem> getInventoryItemsByCategory(String sellerId, String category) {
        return inventoryItemRepository.findBySellerIdAndCategory(sellerId, category);
    }

    /**
     * Get low stock items for a seller.
     */
    public List<InventoryItem> getLowStockItems(String sellerId) {
        return inventoryItemRepository.findBySellerIdAndStockQuantityLessThanEqual(sellerId, 5);
    }

    /**
     * Get out of stock items for a seller.
     */
    public List<InventoryItem> getOutOfStockItems(String sellerId) {
        return inventoryItemRepository.findBySellerIdAndIsActiveAndStockQuantityEquals(sellerId, true, 0);
    }

    /**
     * Get a specific inventory item.
     */
    public Optional<InventoryItem> getInventoryItem(String sellerId, String productId) {
        return inventoryItemRepository.findBySellerIdAndProductId(sellerId, productId);
    }



    /**
     * Update inventory statistics.
     */
    public void updateInventoryStats(String sellerId) {
        Optional<Inventory> optionalInventory = inventoryRepository.findBySellerId(sellerId);
        
        if (optionalInventory.isPresent()) {
            Inventory inventory = optionalInventory.get();
            long totalItems = inventoryItemRepository.countBySellerId(sellerId);
            long lowStockItems = inventoryItemRepository.countBySellerIdAndStockQuantityLessThanEqual(sellerId, 5);
            
            inventory.setTotalItems((int) totalItems);
            inventory.setLowStockItems((int) lowStockItems);
            inventory.setUpdatedAt(new Date());
            
            inventoryRepository.save(inventory);
        }
    }
}
