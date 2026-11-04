package com.wovely.products.clients;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.wovely.common.error.ApiException;
import com.wovely.products.models.Product;

/**
 * Typed client for inventory's stock API (PLAN.md 4.2 / 4.7). Inventory owns stock: products registers
 * new listings, forwards seller stock edits and deactivates archived listings. Inventory's 4xx answers
 * are passed on with their status and message; an unreachable inventory is a 503.
 */
@Component
public class InventoryClient {

    /** What inventory answers after a change. */
    public record StockLevel(String productId, String sellerId, Integer stockQuantity, Integer lowStockThreshold,
            Boolean active, Long version) {
    }

    private final RestTemplate rest;
    private final String base;

    public InventoryClient(RestTemplate rest,
            @Value("${inventory.internal.url:http://localhost:8083/internal/inventory}") String inventoryInternalUrl) {
        this.rest = rest;
        this.base = inventoryInternalUrl + "/stock/{productId}";
    }

    /** Registers the listing's stock (idempotent; the quantity only applies on first registration). */
    public StockLevel register(Product p, int initialQuantity) {
        Map<String, Object> body = new HashMap<>();
        body.put("sellerId", p.getSellerId());
        body.put("initialQuantity", initialQuantity);
        body.put("lowStockThreshold", p.getLowStockThreshold());
        body.put("productName", p.getName());
        body.put("price", p.getPrice());
        body.put("imageUrl", p.getImageUrl());
        body.put("category", p.getCategory());
        body.put("co2EmissionScore", p.getCo2EmissionScore());
        body.put("shippingMethod", p.getShippingMethod());
        body.put("isHandmade", p.getHandmade());
        return call(() -> rest.exchange(base, org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(body), StockLevel.class, p.getId()).getBody());
    }

    public StockLevel restock(String productId, String sellerId, int quantity) {
        return call(() -> rest.postForObject(base + "/restock", Map.of("sellerId", sellerId, "quantity", quantity),
                StockLevel.class, productId));
    }

    public StockLevel setLevel(String productId, String sellerId, int quantity) {
        return call(() -> rest.exchange(base + "/level", org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(Map.of("sellerId", sellerId, "quantity", quantity)),
                StockLevel.class, productId).getBody());
    }

    public StockLevel deactivate(String productId, String sellerId) {
        return call(() -> rest.exchange(base + "?sellerId={sellerId}", org.springframework.http.HttpMethod.DELETE, null,
                StockLevel.class, productId, sellerId).getBody());
    }

    private static StockLevel call(java.util.function.Supplier<StockLevel> request) {
        try {
            return request.get();
        } catch (HttpStatusCodeException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status != null && status.is4xxClientError()) {
                throw new ApiException(status, messageOf(e));
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Inventory service error");
        } catch (ResourceAccessException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Inventory service unavailable");
        }
    }

    private static String messageOf(HttpStatusCodeException e) {
        String body = e.getResponseBodyAsString();
        int i = body.indexOf("\"message\":\"");
        if (i >= 0) {
            int start = i + 11, end = body.indexOf('"', start);
            if (end > start) return body.substring(start, end);
        }
        return "Inventory rejected the request";
    }
}
