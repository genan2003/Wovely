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
public class InventoryController {

    @org.springframework.beans.factory.annotation.Autowired
    com.wovely.inventory.stock.StockLedger stockLedger;

    @Autowired
    private InventoryService inventoryService;

    /**
     * Get seller's complete visual inventory.
     * Returns items with images, names, and stock levels - no complex SKU codes.
     */
    @GetMapping("/seller/{sellerId}")
    @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
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
    @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
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
    @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
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
    @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
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
    @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
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

    /** Set the absolute stock level (single writer: StockLedger, PLAN.md 4.2). */
    @PutMapping("/seller/{sellerId}/product/{productId}/stock")
    @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
    public ResponseEntity<?> updateStock(@PathVariable String sellerId, @PathVariable String productId,
                                          @RequestBody Map<String, Integer> stockRequest) {
        Integer quantity = stockRequest == null ? null : stockRequest.get("quantity");
        if (quantity == null) {
            throw com.wovely.common.error.ApiException.badRequest("Quantity is required");
        }
        InventoryItem item = stockLedger.setLevel(productId, sellerId, quantity);
        inventoryService.updateInventoryStats(sellerId);
        return ResponseEntity.ok(toVisualInventoryItem(item));
    }

    /** Add units to the stock level. */
    @PostMapping("/seller/{sellerId}/product/{productId}/restock")
    @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
    public ResponseEntity<?> restockProduct(@PathVariable String sellerId, @PathVariable String productId,
                                             @RequestBody Map<String, Integer> restockRequest) {
        Integer quantity = restockRequest == null ? null : restockRequest.get("quantity");
        if (quantity == null || quantity <= 0) {
            throw com.wovely.common.error.ApiException.badRequest("Valid quantity is required");
        }
        InventoryItem item = stockLedger.restock(productId, sellerId, quantity);
        inventoryService.updateInventoryStats(sellerId);
        return ResponseEntity.ok(toVisualInventoryItem(item));
    }

    // Listings are created and archived in the products service, which registers/deactivates their
    // stock here over /internal/inventory/stock (PLAN.md 4.2) — no create/delete endpoints on this API.

    /**
     * Get inventory summary/dashboard data.
     */
    @GetMapping("/seller/{sellerId}/dashboard")
    @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
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
        visual.put("productName", item.getProductName());
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
