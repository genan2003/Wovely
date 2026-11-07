package com.wovely.products.services;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.wovely.common.error.ApiException;
import com.wovely.products.models.Product;
import com.wovely.products.repository.ProductRepository;

/** Seller-side read views of their own listings (archived ones excluded). */
@Service
public class SellerListingQueries {

    static final String UNCATEGORISED = "Uncategorised";
    static final int PREVIEW_SIZE = 5;

    private final ProductRepository products;

    public SellerListingQueries(ProductRepository products) {
        this.products = products;
    }

    public List<Product> listings(String sellerId) {
        return products.findBySellerIdAndStatusNot(sellerId, Product.ARCHIVED);
    }

    public Map<String, Object> summary(String sellerId) {
        List<Product> all = listings(sellerId);
        Map<String, Object> response = new HashMap<>();
        response.put("items", views(all));
        response.put("totalItems", all.size());
        response.put("lowStockCount", all.stream().filter(Product::isLowStock).count());
        response.put("outOfStockCount", all.stream().filter(Product::isOutOfStock).count());
        response.put("pendingReviewCount", all.stream().filter(p -> ListingService.PENDING.equals(p.getStatus())).count());
        response.put("needsChangesCount", all.stream().filter(p -> ListingService.NEEDS_CHANGES.equals(p.getStatus())).count());
        return response;
    }

    public Map<String, Object> dashboard(String sellerId) {
        List<Product> all = listings(sellerId);
        Map<String, Long> byCategory = new TreeMap<>();
        all.forEach(p -> byCategory.merge(categoryOf(p), 1L, Long::sum));

        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("totalProducts", all.size());
        dashboard.put("inStock", all.stream().filter(p -> p.getStockQuantity() != null && p.getStockQuantity() > 0).count());
        dashboard.put("lowStock", all.stream().filter(Product::isLowStock).count());
        dashboard.put("outOfStock", all.stream().filter(Product::isOutOfStock).count());
        dashboard.put("categoryBreakdown", byCategory);
        dashboard.put("lowStockPreview", views(all.stream().filter(Product::isLowStock)
            .sorted((a, b) -> Integer.compare(a.getStockQuantity(), b.getStockQuantity()))
            .limit(PREVIEW_SIZE).toList()));
        return dashboard;
    }

    public List<Map<String, Object>> lowStock(String sellerId) {
        return views(listings(sellerId).stream().filter(Product::isLowStock).toList());
    }

    public List<Map<String, Object>> outOfStock(String sellerId) {
        return views(listings(sellerId).stream().filter(Product::isOutOfStock).toList());
    }

    public List<Map<String, Object>> byCategory(String sellerId, String category) {
        return views(products.findBySellerIdAndCategoryAndStatusNot(sellerId, category, Product.ARCHIVED));
    }

    public Map<String, Object> one(String sellerId, String productId) {
        return products.findBySellerIdAndId(sellerId, productId)
            .filter(p -> !Product.ARCHIVED.equals(p.getStatus()))
            .map(SellerListingQueries::toView)
            .orElseThrow(() -> ApiException.notFound("Product not found"));
    }

    static List<Map<String, Object>> views(List<Product> list) {
        return list.stream().map(SellerListingQueries::toView).toList();
    }

    static String categoryOf(Product p) {
        return p.getCategory() == null || p.getCategory().isBlank() ? UNCATEGORISED : p.getCategory();
    }

    /** The JSON shape the seller UI reads ({@code models/inventory.model.ts}). */
    public static Map<String, Object> toView(Product product) {
        Map<String, Object> visual = new LinkedHashMap<>();
        visual.put("id", product.getId());
        visual.put("productId", product.getId());
        visual.put("name", product.getName());
        visual.put("productName", product.getName());
        visual.put("description", product.getDescription());
        visual.put("imageUrl", product.getImageUrl());
        visual.put("category", product.getCategory());
        visual.put("price", product.getPrice());
        visual.put("priceCents", product.getPriceCents());
        visual.put("stockQuantity", product.getStockQuantity() != null ? product.getStockQuantity() : 0);
        visual.put("lowStockThreshold", product.getLowStockThreshold() != null ? product.getLowStockThreshold()
            : Product.DEFAULT_LOW_STOCK_THRESHOLD);
        visual.put("isLowStock", product.isLowStock());
        visual.put("isOutOfStock", product.isOutOfStock());
        visual.put("co2EmissionScore", product.getCo2EmissionScore());
        visual.put("shippingMethod", product.getShippingMethod());
        visual.put("localPickup", Boolean.TRUE.equals(product.getLocalPickup()));
        visual.put("deliveryEmission", product.getDeliveryEmission());
        visual.put("isHandmade", product.getHandmade());
        visual.put("status", product.getStatus());
        visual.put("categoryPath", product.getCategoryPath());
        visual.put("materials", product.getMaterials());
        visual.put("city", product.getCity());
        visual.put("region", product.getRegion());
        visual.put("latitude", product.getLatitude());
        visual.put("longitude", product.getLongitude());
        return visual;
    }
}
