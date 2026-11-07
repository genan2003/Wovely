package com.wovely.inventory.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wovely.common.error.ApiException;
import com.wovely.inventory.models.InventoryItem;
import com.wovely.inventory.services.InventoryService;
import com.wovely.inventory.stock.StockLedger;

/**
 * Seller inventory views (SKU-free: images, names and stock levels). Reads go through
 * {@link InventoryService}; the two stock writes go through {@link StockLedger}, the single writer
 * (ADR 0001). Errors are mapped by the shared {@code GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/inventory/seller/{sellerId}")
@PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
public class InventoryController {

    private final InventoryService inventoryService;
    private final StockLedger stockLedger;

    public InventoryController(InventoryService inventoryService, StockLedger stockLedger) {
        this.inventoryService = inventoryService;
        this.stockLedger = stockLedger;
    }

    @GetMapping
    public Map<String, Object> getSellerInventory(@PathVariable String sellerId) {
        return inventoryService.summary(sellerId);
    }

    @GetMapping("/category/{category}")
    public Map<String, Object> getInventoryByCategory(@PathVariable String sellerId, @PathVariable String category) {
        return Map.of("items", views(inventoryService.getInventoryItemsByCategory(sellerId, category)),
                "category", category);
    }

    @GetMapping("/low-stock")
    public Map<String, Object> getLowStockAlerts(@PathVariable String sellerId) {
        return Map.of("items", views(inventoryService.getLowStockItems(sellerId)), "alertType", "LOW_STOCK");
    }

    @GetMapping("/out-of-stock")
    public Map<String, Object> getOutOfStockItems(@PathVariable String sellerId) {
        return Map.of("items", views(inventoryService.getOutOfStockItems(sellerId)), "alertType", "OUT_OF_STOCK");
    }

    @GetMapping("/product/{productId}")
    public Map<String, Object> getInventoryItem(@PathVariable String sellerId, @PathVariable String productId) {
        return inventoryService.getInventoryItem(sellerId, productId).map(InventoryService::toView)
                .orElseThrow(() -> ApiException.notFound("No stock record for this product"));
    }

    @GetMapping("/dashboard")
    public Map<String, Object> getInventoryDashboard(@PathVariable String sellerId) {
        return inventoryService.dashboard(sellerId);
    }

    /** Set the absolute stock level. */
    @PutMapping("/product/{productId}/stock")
    public Map<String, Object> updateStock(@PathVariable String sellerId, @PathVariable String productId,
            @RequestBody(required = false) Map<String, Integer> body) {
        Integer quantity = body == null ? null : body.get("quantity");
        if (quantity == null) {
            throw ApiException.badRequest("Quantity is required");
        }
        return written(sellerId, stockLedger.setLevel(productId, sellerId, quantity));
    }

    /** Add units to the stock level. */
    @PostMapping("/product/{productId}/restock")
    public Map<String, Object> restockProduct(@PathVariable String sellerId, @PathVariable String productId,
            @RequestBody(required = false) Map<String, Integer> body) {
        Integer quantity = body == null ? null : body.get("quantity");
        if (quantity == null || quantity <= 0) {
            throw ApiException.badRequest("Valid quantity is required");
        }
        return written(sellerId, stockLedger.restock(productId, sellerId, quantity));
    }

    // Listings are created and archived in the products service, which registers/deactivates their
    // stock here over /internal/inventory/stock (PLAN.md 4.2) — no create/delete endpoints on this API.

    private Map<String, Object> written(String sellerId, InventoryItem item) {
        inventoryService.updateInventoryStats(sellerId);
        return InventoryService.toView(item);
    }

    private static List<Map<String, Object>> views(List<InventoryItem> items) {
        return items.stream().map(InventoryService::toView).toList();
    }
}
