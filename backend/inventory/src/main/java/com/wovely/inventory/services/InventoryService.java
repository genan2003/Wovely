package com.wovely.inventory.services;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;

import com.wovely.inventory.models.Inventory;
import com.wovely.inventory.models.InventoryItem;
import com.wovely.inventory.repository.InventoryItemRepository;
import com.wovely.inventory.repository.InventoryRepository;

/**
 * Read side of the seller inventory views. All stock writes go through
 * {@link com.wovely.inventory.stock.StockLedger}.
 *
 * <p>Every view only shows <em>active</em> stock records: archived listings are deactivated by the
 * products service (PLAN.md 2.8) and must not reappear in a seller's alerts or counters. "Low stock"
 * uses each item's own {@code lowStockThreshold}, not a fixed number.
 */
@Service
public class InventoryService {

    static final String UNCATEGORISED = "Uncategorised";
    static final int PREVIEW_SIZE = 5;

    private final InventoryRepository inventoryRepository;
    private final InventoryItemRepository inventoryItemRepository;

    public InventoryService(InventoryRepository inventoryRepository, InventoryItemRepository inventoryItemRepository) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryItemRepository = inventoryItemRepository;
    }

    /** Active stock records of a seller. */
    public List<InventoryItem> getInventoryItems(String sellerId) {
        return inventoryItemRepository.findBySellerIdAndIsActive(sellerId, true);
    }

    public List<InventoryItem> getInventoryItemsByCategory(String sellerId, String category) {
        return activeWhere(sellerId, i -> category.equalsIgnoreCase(categoryOf(i)));
    }

    /** Items at or below their own threshold, out-of-stock ones included. */
    public List<InventoryItem> getLowStockItems(String sellerId) {
        return activeWhere(sellerId, InventoryService::isLow);
    }

    public List<InventoryItem> getOutOfStockItems(String sellerId) {
        return activeWhere(sellerId, InventoryService::isOut);
    }

    public Optional<InventoryItem> getInventoryItem(String sellerId, String productId) {
        return inventoryItemRepository.findBySellerIdAndProductId(sellerId, productId);
    }

    /** Counters for the list view. */
    public Map<String, Object> summary(String sellerId) {
        List<InventoryItem> items = getInventoryItems(sellerId);
        Map<String, Object> response = new HashMap<>();
        response.put("items", items.stream().map(InventoryService::toView).toList());
        response.put("totalItems", items.size());
        response.put("lowStockCount", items.stream().filter(InventoryService::isLow).count());
        response.put("outOfStockCount", items.stream().filter(InventoryService::isOut).count());
        return response;
    }

    /** Dashboard: totals, a per-category breakdown and the first low-stock items. */
    public Map<String, Object> dashboard(String sellerId) {
        List<InventoryItem> items = getInventoryItems(sellerId);
        Map<String, Long> byCategory = new TreeMap<>();
        items.forEach(i -> byCategory.merge(categoryOf(i), 1L, Long::sum));

        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("totalProducts", items.size());
        dashboard.put("inStock", items.stream().filter(i -> quantity(i) > 0).count());
        dashboard.put("lowStock", items.stream().filter(InventoryService::isLow).count());
        dashboard.put("outOfStock", items.stream().filter(InventoryService::isOut).count());
        dashboard.put("categoryBreakdown", byCategory);
        dashboard.put("lowStockPreview", items.stream().filter(InventoryService::isLow)
                .sorted((a, b) -> Integer.compare(quantity(a), quantity(b)))
                .limit(PREVIEW_SIZE).map(InventoryService::toView).toList());
        return dashboard;
    }

    /** Refreshes the cached counters on the seller's {@link Inventory} document, if there is one. */
    public void updateInventoryStats(String sellerId) {
        inventoryRepository.findBySellerId(sellerId).ifPresent(inventory -> {
            List<InventoryItem> items = getInventoryItems(sellerId);
            inventory.setTotalItems(items.size());
            inventory.setLowStockItems((int) items.stream().filter(InventoryService::isLow).count());
            inventory.setUpdatedAt(new Date());
            inventoryRepository.save(inventory);
        });
    }

    /** The JSON shape the seller UI reads (`models/inventory.model.ts`). */
    public static Map<String, Object> toView(InventoryItem item) {
        Map<String, Object> visual = new LinkedHashMap<>();
        visual.put("id", item.getId());
        visual.put("productId", item.getProductId());
        visual.put("name", item.getProductName());
        visual.put("productName", item.getProductName());
        visual.put("imageUrl", item.getImageUrl());
        visual.put("category", item.getCategory());
        visual.put("price", item.getPrice());
        visual.put("priceCents", item.getPriceCents());
        visual.put("stockQuantity", quantity(item));
        visual.put("lowStockThreshold", threshold(item));
        visual.put("isLowStock", isLow(item));
        visual.put("isOutOfStock", isOut(item));
        visual.put("co2EmissionScore", item.getCo2EmissionScore());
        visual.put("shippingMethod", item.getShippingMethod());
        visual.put("isHandmade", item.getIsHandmade());
        visual.put("lastRestockedAt", item.getLastRestockedAt());
        return visual;
    }

    private List<InventoryItem> activeWhere(String sellerId, Predicate<InventoryItem> filter) {
        return getInventoryItems(sellerId).stream().filter(filter).toList();
    }

    // Null-safe: legacy records may lack a quantity, threshold or category.
    static int quantity(InventoryItem i) {
        return i.getStockQuantity() == null ? 0 : i.getStockQuantity();
    }

    static int threshold(InventoryItem i) {
        return i.getLowStockThreshold() == null ? 5 : i.getLowStockThreshold();
    }

    static boolean isLow(InventoryItem i) {
        return quantity(i) <= threshold(i);
    }

    static boolean isOut(InventoryItem i) {
        return quantity(i) <= 0;
    }

    static String categoryOf(InventoryItem i) {
        return i.getCategory() == null || i.getCategory().isBlank() ? UNCATEGORISED : i.getCategory();
    }
}
