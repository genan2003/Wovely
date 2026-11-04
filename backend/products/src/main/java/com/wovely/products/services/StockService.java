package com.wovely.products.services;

import org.springframework.stereotype.Service;

import com.wovely.products.clients.InventorySyncClient;
import com.wovely.products.exception.ApiException;
import com.wovely.products.models.Product;
import com.wovely.products.repository.ProductRepository;

/** Stock changes shared by the seller UI (restock) and the internal order flow (reduce/restock). */
@Service
public class StockService {

    private final ProductRepository productRepository;
    private final InventorySyncClient inventorySync;

    public StockService(ProductRepository productRepository, InventorySyncClient inventorySync) {
        this.productRepository = productRepository;
        this.inventorySync = inventorySync;
    }

    /**
     * Deducts stock. Throws 404 if the product does not belong to the seller, 409 if stock is short.
     * NOTE: read-check-write, not atomic yet — see PLAN.md 2.2.
     */
    public Product reduce(String sellerId, String productId, Integer quantity) {
        int qty = requirePositive(quantity);
        Product product = load(sellerId, productId);
        int available = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
        if (available < qty) {
            throw ApiException.conflict("Not enough stock available (available: " + available
                    + ", requested: " + qty + ")");
        }
        product.setStockQuantity(available - qty);
        return saveAndSync(product);
    }

    public Product restock(String sellerId, String productId, Integer quantity) {
        int qty = requirePositive(quantity);
        Product product = load(sellerId, productId);
        int current = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
        product.setStockQuantity(current + qty);
        return saveAndSync(product);
    }

    private Product load(String sellerId, String productId) {
        return productRepository.findBySellerIdAndId(sellerId, productId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
    }

    private Product saveAndSync(Product product) {
        Product saved = productRepository.save(product);
        inventorySync.sync(saved);
        return saved;
    }

    private static int requirePositive(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw ApiException.badRequest("Valid quantity is required");
        }
        return quantity;
    }
}
