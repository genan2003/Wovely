package com.wovely.wovely.clients;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.wovely.wovely.exception.ApiException;

/**
 * Calls to the products service (catalog reads + internal stock endpoints) and the
 * best-effort inventory mirror. Translates remote errors into {@link ApiException}s.
 */
@Component
public class CatalogClient {

    private static final Logger logger = LoggerFactory.getLogger(CatalogClient.class);

    /** The fields of a product the core service relies on. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductSnapshot(String id, String name, Double price, String sellerId, String imageUrl,
            String status, Integer stockQuantity) {
    }

    private final RestTemplate restTemplate;
    private final String productsApi;
    private final String productsInternal;
    private final String inventoryInternal;

    public CatalogClient(RestTemplate restTemplate,
            @Value("${products.api.url:http://localhost:8082/api/products}") String productsApi,
            @Value("${products.internal.url:http://localhost:8082/internal/products}") String productsInternal,
            @Value("${inventory.internal.url:http://localhost:8083/internal/inventory}") String inventoryInternal) {
        this.restTemplate = restTemplate;
        this.productsApi = productsApi;
        this.productsInternal = productsInternal;
        this.inventoryInternal = inventoryInternal;
    }

    /** Server-side source of truth for name, price and seller of a product. 400 if it does not exist. */
    public ProductSnapshot getProduct(String productId) {
        try {
            ProductSnapshot p = restTemplate.getForObject(productsApi + "/{id}", ProductSnapshot.class, productId);
            if (p == null) {
                throw ApiException.badRequest("Product not found: " + productId);
            }
            return p;
        } catch (HttpClientErrorException.NotFound e) {
            throw ApiException.badRequest("Product not found: " + productId);
        } catch (RestClientException e) {
            logger.error("Products service unavailable: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Products service unavailable");
        }
    }

    /** Deducts stock in the products service (authoritative); 409 when stock is short. */
    public void reduceStock(String sellerId, String productId, int quantity) {
        try {
            restTemplate.postForEntity(productsInternal + "/seller/{s}/product/{p}/reduce-stock",
                    Map.of("quantity", quantity), Map.class, sellerId, productId);
        } catch (HttpClientErrorException.Conflict e) {
            throw ApiException.conflict("Insufficient stock for product " + productId);
        } catch (HttpClientErrorException.NotFound e) {
            throw ApiException.badRequest("Product not found: " + productId);
        } catch (RestClientException e) {
            logger.error("Stock deduction failed for {}: {}", productId, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Stock deduction failed");
        }
        mirrorInventory("/reduce-stock", sellerId, productId, quantity);
    }

    /** Restores stock; failures are logged, never thrown (used in compensation paths). */
    public void restock(String sellerId, String productId, int quantity) {
        try {
            restTemplate.postForEntity(productsInternal + "/seller/{s}/product/{p}/restock",
                    Map.of("quantity", quantity), Map.class, sellerId, productId);
        } catch (RestClientException e) {
            logger.error("Restock failed for {} (qty {}): {}", productId, quantity, e.getMessage());
            return;
        }
        mirrorInventory("/restock", sellerId, productId, quantity);
    }

    private void mirrorInventory(String op, String sellerId, String productId, int quantity) {
        try {
            restTemplate.postForEntity(inventoryInternal + "/seller/{s}/product/{p}" + op,
                    Map.of("quantity", quantity), Map.class, sellerId, productId);
        } catch (RestClientException e) {
            logger.warn("Inventory mirror {} failed for {}: {}", op, productId, e.getMessage());
        }
    }
}
