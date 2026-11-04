package com.wovely.inventory.controllers;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wovely.inventory.exception.ApiException;
import com.wovely.inventory.models.InventoryItem;
import com.wovely.inventory.services.InventoryService;

/**
 * Service-to-service endpoints (core and products services). Protected by the internal API key —
 * never exposed to browsers. See PLAN.md 1.3.
 */
@RestController
@RequestMapping("/internal/inventory/seller/{sellerId}/product/{productId}")
public class InternalInventoryController {

    private final InventoryService inventoryService;

    public InternalInventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /** Mirror product details/stock from the products service. */
    @PutMapping
    public ResponseEntity<?> sync(@PathVariable String sellerId, @PathVariable String productId,
            @RequestBody InventoryItem details) {
        InventoryItem item = inventoryService.syncProductDetails(sellerId, productId, details)
                .orElseThrow(() -> ApiException.notFound("Inventory item not found"));
        inventoryService.updateInventoryStats(sellerId);
        return ResponseEntity.ok(stock(item));
    }

    @PostMapping("/reduce-stock")
    public ResponseEntity<?> reduceStock(@PathVariable String sellerId, @PathVariable String productId,
            @RequestBody Map<String, Integer> body) {
        int quantity = validQuantity(body);
        InventoryItem item = inventoryService.getInventoryItem(sellerId, productId)
                .orElseThrow(() -> ApiException.notFound("Inventory item not found"));
        if (item.getStockQuantity() < quantity) {
            throw ApiException.conflict("Not enough stock available (available: " + item.getStockQuantity()
                    + ", requested: " + quantity + ")");
        }
        InventoryItem updated = inventoryService.reduceStock(sellerId, productId, quantity)
                .orElseThrow(() -> ApiException.notFound("Inventory item not found"));
        inventoryService.updateInventoryStats(sellerId);
        return ResponseEntity.ok(stock(updated));
    }

    @PostMapping("/restock")
    public ResponseEntity<?> restock(@PathVariable String sellerId, @PathVariable String productId,
            @RequestBody Map<String, Integer> body) {
        int quantity = validQuantity(body);
        InventoryItem updated = inventoryService.restock(sellerId, productId, quantity)
                .orElseThrow(() -> ApiException.notFound("Inventory item not found"));
        inventoryService.updateInventoryStats(sellerId);
        return ResponseEntity.ok(stock(updated));
    }

    private static int validQuantity(Map<String, Integer> body) {
        Integer q = body == null ? null : body.get("quantity");
        if (q == null || q <= 0) {
            throw ApiException.badRequest("Valid quantity is required");
        }
        return q;
    }

    private static Map<String, Object> stock(InventoryItem item) {
        return Map.of("productId", item.getProductId(), "stockQuantity", item.getStockQuantity());
    }
}
