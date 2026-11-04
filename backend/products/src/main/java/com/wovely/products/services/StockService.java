package com.wovely.products.services;

import org.springframework.stereotype.Service;

import com.wovely.common.error.ApiException;
import com.wovely.products.clients.InventoryClient;
import com.wovely.products.models.Product;
import com.wovely.products.repository.ProductRepository;

/**
 * Seller stock edits (PLAN.md 4.2): inventory owns stock, so these forward to it and then apply
 * inventory's answer to the local read model right away — the seller sees their own change without
 * waiting for the event, and the later event is a no-op (same version).
 */
@Service
public class StockService {

    private final InventoryClient inventory;
    private final StockReadModel readModel;
    private final ProductRepository products;

    public StockService(InventoryClient inventory, StockReadModel readModel, ProductRepository products) {
        this.inventory = inventory;
        this.readModel = readModel;
        this.products = products;
    }

    public Product restock(String sellerId, String productId, Integer quantity) {
        Product p = owned(sellerId, productId);
        return applied(p, inventory.restock(productId, sellerId, positive(quantity)));
    }

    public Product setLevel(String sellerId, String productId, Integer quantity) {
        if (quantity == null || quantity < 0) {
            throw ApiException.badRequest("quantity must be zero or more");
        }
        Product p = owned(sellerId, productId);
        return applied(p, inventory.setLevel(productId, sellerId, quantity));
    }

    /** New listing: register its stock with inventory and seed the read model. */
    public Product register(Product saved, int initialQuantity) {
        return applied(saved, inventory.register(saved, initialQuantity));
    }

    /**
     * After any full save of a listing: refresh inventory's copy of the details (idempotent; bumps the
     * version, which also heals a read-model value a concurrent save may have overwritten) or deactivate
     * it when archived. Best effort — a missed refresh is repaired by the next stock event.
     */
    public void afterListingSaved(Product saved) {
        try {
            if (Product.ARCHIVED.equals(saved.getStatus())) {
                inventory.deactivate(saved.getId(), saved.getSellerId());
            } else {
                applied(saved, inventory.register(saved, 0));
            }
        } catch (ApiException e) {
            org.slf4j.LoggerFactory.getLogger(StockService.class)
                .warn("Inventory refresh for product {} failed: {}", saved.getId(), e.getMessage());
        }
    }

    private Product applied(Product p, InventoryClient.StockLevel level) {
        if (level != null && level.version() != null && level.stockQuantity() != null) {
            readModel.apply(p.getId(), level.stockQuantity(), level.lowStockThreshold(), level.version());
            return products.findById(p.getId()).orElse(p);
        }
        return p;
    }

    private Product owned(String sellerId, String productId) {
        return products.findBySellerIdAndId(sellerId, productId).orElseThrow(() -> ApiException.notFound("Product not found"));
    }

    private static int positive(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw ApiException.badRequest("Valid quantity is required");
        }
        return quantity;
    }
}
