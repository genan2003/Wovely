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
     * Add a product to seller's inventory.
     */
    public InventoryItem addToInventory(InventoryItem item) {
        item.setCreatedAt(new Date());
        item.setUpdatedAt(new Date());
        return inventoryItemRepository.save(item);
    }

    /**
     * Update stock quantity for an inventory item.
     */
    public Optional<InventoryItem> updateStock(String sellerId, String productId, int quantity) {
        Optional<InventoryItem> optionalItem = inventoryItemRepository.findBySellerIdAndProductId(sellerId, productId);
        
        if (optionalItem.isPresent()) {
            InventoryItem item = optionalItem.get();
            item.setStockQuantity(quantity);
            item.setUpdatedAt(new Date());
            
            if (quantity > 0 && item.getStockQuantity() == 0) {
                item.setLastRestockedAt(new Date());
            }
            
            return Optional.of(inventoryItemRepository.save(item));
        }
        
        return Optional.empty();
    }

    /**
     * Reduce stock for an item (when an order is made).
     */
    public Optional<InventoryItem> reduceStock(String sellerId, String productId, int quantity) {
        Optional<InventoryItem> optionalItem = inventoryItemRepository.findBySellerIdAndProductId(sellerId, productId);
        
        if (optionalItem.isPresent()) {
            InventoryItem item = optionalItem.get();
            item.reduceStock(quantity);
            return Optional.of(inventoryItemRepository.save(item));
        }
        
        return Optional.empty();
    }

    /**
     * Restock an item.
     */
    public Optional<InventoryItem> restock(String sellerId, String productId, int quantity) {
        Optional<InventoryItem> optionalItem = inventoryItemRepository.findBySellerIdAndProductId(sellerId, productId);
        
        if (optionalItem.isPresent()) {
            InventoryItem item = optionalItem.get();
            item.addStock(quantity);
            return Optional.of(inventoryItemRepository.save(item));
        }
        
        return Optional.empty();
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
     * Remove a product from inventory (soft delete).
     */
    public Optional<InventoryItem> removeFromInventory(String sellerId, String productId) {
        Optional<InventoryItem> optionalItem = inventoryItemRepository.findBySellerIdAndProductId(sellerId, productId);
        
        if (optionalItem.isPresent()) {
            InventoryItem item = optionalItem.get();
            item.setIsActive(false);
            item.setUpdatedAt(new Date());
            return Optional.of(inventoryItemRepository.save(item));
        }
        
        return Optional.empty();
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
