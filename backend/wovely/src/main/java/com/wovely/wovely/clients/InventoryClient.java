package com.wovely.wovely.clients;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.wovely.common.error.ApiException;

/**
 * Typed client for inventory, the owner of stock (PLAN.md 4.2 / 4.7).
 * <ul>
 *   <li>{@link #reserve}: atomic deduction at checkout / order edit; 409 is passed on as is.</li>
 *   <li>{@link #release}: gives stock back (cancel, refund, return, compensation). It must not be lost,
 *       so if inventory can't be reached the release is stored in {@code stock_release_retries} and
 *       replayed by {@link #retryPendingReleases()} until it succeeds.</li>
 * </ul>
 */
@Component
public class InventoryClient {

    private static final Logger logger = LoggerFactory.getLogger(InventoryClient.class);

    @Document(collection = "stock_release_retries")
    public static class PendingRelease {
        @Id
        String id;
        String sellerId;
        String productId;
        int quantity;
        int attempts;
        Date createdAt = new Date();
        String lastError;
    }

    private final RestTemplate rest;
    private final MongoTemplate mongo;
    private final String base;

    public InventoryClient(RestTemplate rest, MongoTemplate mongo,
            @Value("${inventory.internal.url:http://localhost:8083/internal/inventory}") String inventoryInternalUrl) {
        this.rest = rest;
        this.mongo = mongo;
        this.base = inventoryInternalUrl + "/stock/{productId}";
    }

    public void reserve(String sellerId, String productId, int quantity) {
        try {
            rest.postForEntity(base + "/reserve", Map.of("sellerId", sellerId, "quantity", quantity), Map.class, productId);
        } catch (HttpClientErrorException.Conflict e) {
            throw ApiException.conflict("Insufficient stock for product " + productId);
        } catch (HttpClientErrorException.NotFound e) {
            throw ApiException.badRequest("Product not available: " + productId);
        } catch (RestClientException e) {
            logger.error("Stock reservation failed for {}: {}", productId, e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Inventory service unavailable");
        }
    }

    /** Never throws: stock that can't be returned now is queued and retried. */
    public void release(String sellerId, String productId, int quantity) {
        try {
            send(sellerId, productId, quantity);
        } catch (RestClientException e) {
            logger.warn("Release of {} x{} queued for retry: {}", productId, quantity, e.getMessage());
            PendingRelease p = new PendingRelease();
            p.sellerId = sellerId;
            p.productId = productId;
            p.quantity = quantity;
            p.attempts = 1;
            p.lastError = e.getMessage();
            mongo.save(p);
        }
    }

    @Scheduled(fixedDelayString = "${inventory.release-retry-ms:30000}", initialDelayString = "${inventory.release-retry-ms:30000}")
    public void retryPendingReleases() {
        List<PendingRelease> pending = mongo.find(new Query().limit(100), PendingRelease.class);
        for (PendingRelease p : pending) {
            try {
                send(p.sellerId, p.productId, p.quantity);
                mongo.remove(new Query(Criteria.where("_id").is(p.id)), PendingRelease.class);
                logger.info("Queued release of {} x{} delivered after {} attempt(s)", p.productId, p.quantity, p.attempts);
            } catch (HttpClientErrorException e) {
                // 4xx won't fix itself (e.g. unknown product): keep it for a human, stop hammering
                logger.error("Queued release of {} x{} rejected by inventory: {}", p.productId, p.quantity, e.getStatusCode());
                mongo.remove(new Query(Criteria.where("_id").is(p.id)), PendingRelease.class);
                mongo.save(p, "stock_release_failures");
            } catch (RestClientException e) {
                mongo.updateFirst(new Query(Criteria.where("_id").is(p.id)),
                        new org.springframework.data.mongodb.core.query.Update().inc("attempts", 1).set("lastError", e.getMessage()),
                        PendingRelease.class);
                return; // inventory still down; try the rest next round
            }
        }
    }

    private void send(String sellerId, String productId, int quantity) {
        rest.postForEntity(base + "/release", Map.of("sellerId", sellerId, "quantity", quantity), Map.class, productId);
    }
}
