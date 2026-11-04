package com.wovely.products.clients;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Asks core whether a buyer has received a product ({@code GET /internal/orders/verify-purchase},
 * internal API key via the shared {@link RestTemplate}). Fails closed: if core can't be reached the
 * review is stored as not verified rather than blocking the buyer (PLAN.md 3.3).
 */
@Component
public class PurchaseVerificationClient {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseVerificationClient.class);

    private final RestTemplate restTemplate;
    private final String coreInternalUrl;

    public PurchaseVerificationClient(RestTemplate restTemplate,
            @Value("${core.internal.url:http://localhost:8081/internal}") String coreInternalUrl) {
        this.restTemplate = restTemplate;
        this.coreInternalUrl = coreInternalUrl;
    }

    public boolean hasReceived(String buyerId, String productId) {
        java.net.URI url = UriComponentsBuilder.fromUriString(coreInternalUrl + "/orders/verify-purchase")
                .queryParam("buyerId", buyerId)
                .queryParam("productId", productId)
                .encode()
                .build()
                .toUri();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restTemplate.getForObject(url, Map.class);
            return body != null && Boolean.TRUE.equals(body.get("verified"));
        } catch (Exception e) {
            logger.warn("Purchase verification for buyer {} / product {} failed: {}", buyerId, productId, e.getMessage());
            return false;
        }
    }
}
