package com.wovely.products.clients;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.wovely.products.models.Product;

/**
 * Best-effort mirror of product details/stock into the inventory service
 * ({@code PUT /internal/inventory/seller/{sellerId}/product/{productId}}, internal API key).
 * Stock ownership is to be settled in PLAN.md 4.2.
 */
@Component
public class InventorySyncClient {

    private static final Logger logger = LoggerFactory.getLogger(InventorySyncClient.class);

    private final RestTemplate restTemplate;
    private final String inventoryInternalUrl;

    public InventorySyncClient(RestTemplate restTemplate,
            @Value("${inventory.internal.url:http://localhost:8083/internal/inventory}") String inventoryInternalUrl) {
        this.restTemplate = restTemplate;
        this.inventoryInternalUrl = inventoryInternalUrl;
    }

    public void sync(Product product) {
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("productName", product.getName());
            details.put("price", product.getPrice());
            details.put("imageUrl", product.getImageUrl());
            details.put("category", product.getCategory());
            details.put("lowStockThreshold", product.getLowStockThreshold());
            details.put("co2EmissionScore", product.getCo2EmissionScore());
            details.put("shippingMethod", product.getShippingMethod());
            details.put("isHandmade", product.getHandmade());
            details.put("stockQuantity", product.getStockQuantity());
            restTemplate.put(inventoryInternalUrl + "/seller/{sellerId}/product/{productId}", details,
                    product.getSellerId(), product.getId());
        } catch (Exception e) {
            logger.warn("Inventory sync failed for product {}: {}", product.getId(), e.getMessage());
        }
    }
}
