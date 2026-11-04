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

import com.wovely.common.error.ApiException;

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

    /** Read-only view of the catalog (name, price, seller, status). Stock lives in inventory: see InventoryClient. */
    public CatalogClient(RestTemplate restTemplate,
            @Value("${products.api.url:http://localhost:8082/api/products}") String productsApi) {
        this.restTemplate = restTemplate;
        this.productsApi = productsApi;
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
}
