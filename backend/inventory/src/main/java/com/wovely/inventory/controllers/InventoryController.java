package com.wovely.inventory.controllers;

import com.wovely.inventory.models.Inventory;
import com.wovely.inventory.models.InventoryItem;
import com.wovely.inventory.models.ManualOrderRequest;
import com.wovely.inventory.services.InventoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for visual inventory management.
 * Provides SKU-free interface for sellers to manage inventory through images.
 */
@RestController
@RequestMapping("/api/inventory")
@CrossOrigin(origins = "${cors.allowed.origins:http://localhost:4200}")
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;

    /**
     * Get seller's complete visual inventory.
     * Returns items with images, names, and stock levels - no complex SKU codes.
     */
    @GetMapping("/seller/{sellerId}")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<?> getSellerInventory(@PathVariable String sellerId) {
        try {
            List<InventoryItem> items = inventoryService.getInventoryItems(sellerId);
            
            // Transform to visual-friendly format
            List<Map<String, Object>> visualItems = items.stream()
                .map(this::toVisualInventoryItem)
                .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("items", visualItems);
            response.put("totalItems", items.size());
            response.put("lowStockCount", (int) items.stream().filter(InventoryItem::isLowStock).count());
            response.put("outOfStockCount", (int) items.stream().filter(InventoryItem::isOutOfStock).count());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get inventory items filtered by category.
     */
    @GetMapping("/seller/{sellerId}/category/{category}")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<?> getInventoryByCategory(@PathVariable String sellerId, 
                                                     @PathVariable String category) {
        try {
            List<InventoryItem> items = inventoryService.getInventoryItemsByCategory(sellerId, category);
            
            List<Map<String, Object>> visualItems = items.stream()
                .map(this::toVisualInventoryItem)
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of("items", visualItems, "category", category));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get low stock alerts.
     */
    @GetMapping("/seller/{sellerId}/low-stock")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<?> getLowStockAlerts(@PathVariable String sellerId) {
        try {
            List<InventoryItem> items = inventoryService.getLowStockItems(sellerId);
            
            List<Map<String, Object>> visualItems = items.stream()
                .map(this::toVisualInventoryItem)
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of("items", visualItems, "alertType", "LOW_STOCK"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get out of stock items.
     */
    @GetMapping("/seller/{sellerId}/out-of-stock")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<?> getOutOfStockItems(@PathVariable String sellerId) {
        try {
            List<InventoryItem> items = inventoryService.getOutOfStockItems(sellerId);
            
            List<Map<String, Object>> visualItems = items.stream()
                .map(this::toVisualInventoryItem)
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of("items", visualItems, "alertType", "OUT_OF_STOCK"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get a specific inventory item by product ID.
     */
    @GetMapping("/seller/{sellerId}/product/{productId}")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<?> getInventoryItem(@PathVariable String sellerId, 
                                               @PathVariable String productId) {
        try {
            return inventoryService.getInventoryItem(sellerId, productId)
                .map(item -> ResponseEntity.ok(toVisualInventoryItem(item)))
                .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update stock quantity for a product.
     */
    @PutMapping("/seller/{sellerId}/product/{productId}/stock")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<?> updateStock(@PathVariable String sellerId,
                                          @PathVariable String productId,
                                          @RequestBody Map<String, Integer> stockRequest) {
        try {
            Integer quantity = stockRequest.get("quantity");
            if (quantity == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Quantity is required"));
            }

            return inventoryService.updateStock(sellerId, productId, quantity)
                .map(item -> {
                    inventoryService.updateInventoryStats(sellerId);
                    return ResponseEntity.ok(toVisualInventoryItem(item));
                })
                .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Restock a product (add to existing stock).
     */
    @PostMapping("/seller/{sellerId}/product/{productId}/restock")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<?> restockProduct(@PathVariable String sellerId,
                                             @PathVariable String productId,
                                             @RequestBody Map<String, Integer> restockRequest) {
        try {
            Integer quantity = restockRequest.get("quantity");
            if (quantity == null || quantity <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Valid quantity is required"));
            }

            return inventoryService.restock(sellerId, productId, quantity)
                .map(item -> {
                    inventoryService.updateInventoryStats(sellerId);
                    return ResponseEntity.ok(toVisualInventoryItem(item));
                })
                .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Add a new product to inventory.
     */
    @PostMapping("/seller/{sellerId}/product")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<?> addProductToInventory(@PathVariable String sellerId,
                                                    @RequestBody InventoryItem item) {
        try {
            item.setSellerId(sellerId);
            InventoryItem savedItem = inventoryService.addToInventory(item);
            inventoryService.updateInventoryStats(sellerId);
            return ResponseEntity.status(HttpStatus.CREATED).body(toVisualInventoryItem(savedItem));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Remove a product from inventory (soft delete).
     */
    @DeleteMapping("/seller/{sellerId}/product/{productId}")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<?> removeProduct(@PathVariable String sellerId,
                                            @PathVariable String productId) {
        try {
            return inventoryService.removeFromInventory(sellerId, productId)
                .map(item -> {
                    inventoryService.updateInventoryStats(sellerId);
                    return ResponseEntity.ok(Map.of("message", "Product removed from inventory", "productId", productId));
                })
                .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get inventory summary/dashboard data.
     */
    @GetMapping("/seller/{sellerId}/dashboard")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<?> getInventoryDashboard(@PathVariable String sellerId) {
        try {
            List<InventoryItem> allItems = inventoryService.getInventoryItems(sellerId);
            
            Map<String, Object> dashboard = new HashMap<>();
            dashboard.put("totalProducts", allItems.size());
            dashboard.put("inStock", (int) allItems.stream().filter(i -> i.getStockQuantity() > 0).count());
            dashboard.put("lowStock", (int) allItems.stream().filter(InventoryItem::isLowStock).count());
            dashboard.put("outOfStock", (int) allItems.stream().filter(InventoryItem::isOutOfStock).count());
            
            // Category breakdown
            Map<String, Long> categoryBreakdown = allItems.stream()
                .collect(Collectors.groupingBy(
                    InventoryItem::getCategory,
                    Collectors.counting()
                ));
            dashboard.put("categoryBreakdown", categoryBreakdown);

            // Low stock items preview (top 5)
            List<Map<String, Object>> lowStockPreview = allItems.stream()
                .filter(InventoryItem::isLowStock)
                .limit(5)
                .map(this::toVisualInventoryItem)
                .collect(Collectors.toList());
            dashboard.put("lowStockPreview", lowStockPreview);

            return ResponseEntity.ok(dashboard);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Convert InventoryItem to visual-friendly map format.
     */
    private Map<String, Object> toVisualInventoryItem(InventoryItem item) {
        Map<String, Object> visual = new HashMap<>();
        visual.put("id", item.getId());
        visual.put("productId", item.getProductId());
        visual.put("name", item.getProductName());
        visual.put("imageUrl", item.getImageUrl());
        visual.put("category", item.getCategory());
        visual.put("price", item.getPrice());
        visual.put("stockQuantity", item.getStockQuantity());
        visual.put("lowStockThreshold", item.getLowStockThreshold());
        visual.put("isLowStock", item.isLowStock());
        visual.put("isOutOfStock", item.isOutOfStock());
        visual.put("co2EmissionScore", item.getCo2EmissionScore());
        visual.put("shippingMethod", item.getShippingMethod());
        visual.put("isHandmade", item.getIsHandmade());
        visual.put("lastRestockedAt", item.getLastRestockedAt());
        return visual;
    }
}
