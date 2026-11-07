package com.wovely.products.services;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.wovely.common.error.ApiException;
import com.wovely.common.security.JwtPrincipal;
import com.wovely.products.clients.AuditClient;
import com.wovely.products.models.Product;
import com.wovely.products.payload.ListingRequest;
import com.wovely.products.repository.ProductRepository;

/**
 * Writes to listings: create, seller edit, admin edit, moderation and archive.
 *
 * <p>Review rules (see {@code docs/diagrams/state/product-status.puml}):
 * <ul>
 *   <li>a new listing starts {@code PENDING};</li>
 *   <li>a seller edit of a {@code NEEDS_CHANGES} listing sends it back to {@code PENDING} (resubmission);</li>
 *   <li>a seller edit that changes what a buyer reads or sees — name, description, image, category,
 *       materials — on an {@code APPROVED} listing sends it back to {@code PENDING}; price, stock,
 *       location and delivery edits stay live;</li>
 *   <li>{@code REJECTED} listings are final (409) and {@code ARCHIVED} ones are gone (404);</li>
 *   <li>admin edits never change the status.</li>
 * </ul>
 * Stock is owned by inventory (ADR 0001) and only changes through {@link StockService}.
 */
@Service
public class ListingService {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String NEEDS_CHANGES = "NEEDS_CHANGES";

    private final ProductRepository products;
    private final StockService stock;
    private final AuditClient audit;

    public ListingService(ProductRepository products, StockService stock, AuditClient audit) {
        this.products = products;
        this.stock = stock;
        this.audit = audit;
    }

    /** The seller is the caller; an admin may list on behalf of {@code sellerId}. */
    public Product create(ListingRequest r, JwtPrincipal caller) {
        String sellerId = caller.isAdmin() && r.sellerId() != null ? r.sellerId() : caller.userId();
        if (sellerId == null) {
            throw ApiException.forbidden("Token has no user id; please sign in again");
        }
        if (r.name() == null || r.name().isBlank() || r.price() == null) {
            throw ApiException.badRequest("name and a non-negative price are required");
        }
        Product p = new Product();
        p.setSellerId(sellerId);
        p.setStatus(PENDING);
        p.setLocalPickup(false);
        p.setLowStockThreshold(Product.DEFAULT_LOW_STOCK_THRESHOLD);
        applyContent(p, r);
        applyDelivery(p, r);
        if (r.lowStockThreshold() != null) {
            p.setLowStockThreshold(r.lowStockThreshold());
        }
        int initialStock = r.stockQuantity() == null ? 0 : r.stockQuantity();
        p.setStockQuantity(initialStock);
        Product saved = products.save(p);
        try {
            // a listing without a stock record can't be sold, so undo on failure
            return stock.register(saved, initialStock);
        } catch (RuntimeException e) {
            products.delete(saved);
            throw e;
        }
    }

    /** Seller edit; may send the listing back to the approval queue (see class doc). */
    public Product updateBySeller(String sellerId, String productId, ListingRequest r) {
        Product p = products.findBySellerIdAndId(sellerId, productId)
            .filter(found -> !Product.ARCHIVED.equals(found.getStatus()))
            .orElseThrow(() -> ApiException.notFound("Product not found"));
        if (REJECTED.equals(p.getStatus())) {
            throw ApiException.conflict("This listing was rejected and can't be edited; create a new listing instead");
        }
        String before = p.getStatus();
        boolean contentChanged = applyContent(p, r);
        applyDelivery(p, r);
        if (NEEDS_CHANGES.equals(before) || (APPROVED.equals(before) && contentChanged)) {
            p.setStatus(PENDING);
        }
        Product saved = save(p, r);
        if (!Objects.equals(before, saved.getStatus())) {
            audit.record("PRODUCT_RESUBMITTED", productId, before + " -> " + saved.getStatus() + " after a seller edit");
        }
        return saved;
    }

    /** Admin content edit (moderation): never changes the status, always audited. */
    public Product editByAdmin(String productId, ListingRequest r) {
        Product p = products.findById(productId).orElseThrow(() -> ApiException.notFound("Product not found"));
        applyContent(p, r);
        applyDelivery(p, r);
        Product saved = save(p, r);
        audit.record("PRODUCT_EDIT_BY_ADMIN", productId, "Listing content edited by admin");
        return saved;
    }

    public Product moderate(String productId, String newStatus, String reason) {
        if (newStatus == null || !Product.STATUSES.contains(newStatus)) {
            throw ApiException.badRequest("status must be one of " + Product.STATUSES);
        }
        Product p = products.findById(productId).orElseThrow(() -> ApiException.notFound("Product not found"));
        String before = p.getStatus();
        p.setStatus(newStatus);
        Product saved = products.save(p);
        stock.afterListingSaved(saved);
        audit.record("PRODUCT_STATUS", productId, before + " -> " + newStatus + (reason != null ? ", Reason: " + reason : ""));
        return saved;
    }

    /**
     * Soft delete: order history keeps resolving the product, it leaves the catalog and seller views,
     * its stock record is deactivated and checkout rejects it.
     */
    public void archive(String sellerId, String productId) {
        Product p = products.findBySellerIdAndId(sellerId, productId)
            .orElseThrow(() -> ApiException.notFound("Product not found"));
        p.setStatus(Product.ARCHIVED);
        products.save(p);
        stock.afterListingSaved(p);
    }

    private Product save(Product p, ListingRequest r) {
        if (r.lowStockThreshold() != null) {
            p.setLowStockThreshold(r.lowStockThreshold());
        }
        Product saved = products.save(p);
        stock.afterListingSaved(saved);
        if (r.stockQuantity() != null) {
            return stock.setLevel(saved.getSellerId(), saved.getId(), r.stockQuantity());
        }
        return products.findById(saved.getId()).orElse(saved);
    }

    /** Applies the reviewed fields; returns whether any of them changed. */
    static boolean applyContent(Product p, ListingRequest r) {
        if (r.name() != null && r.name().isBlank()) {
            throw ApiException.badRequest("name can't be blank");
        }
        boolean changed = false;
        changed |= set(r.name() == null ? null : r.name().trim(), p::getName, p::setName);
        changed |= setText(r.description(), p::getDescription, p::setDescription);
        changed |= setText(r.imageUrl(), p::getImageUrl, p::setImageUrl);
        changed |= setText(r.category(), p::getCategory, p::setCategory);
        changed |= setText(r.categoryPath(), p::getCategoryPath, p::setCategoryPath);
        changed |= set(r.materials() == null ? null : List.copyOf(r.materials()), p::getMaterials, p::setMaterials);
        return changed;
    }

    /** Fields that don't need a new review. */
    static void applyDelivery(Product p, ListingRequest r) {
        set(r.price() == null ? null : cents(r.price()), p::getPriceCents, p::setPriceCents);
        setText(r.city(), p::getCity, p::setCity);
        setText(r.region(), p::getRegion, p::setRegion);
        set(r.latitude(), p::getLatitude, p::setLatitude);
        set(r.longitude(), p::getLongitude, p::setLongitude);
        set(r.co2EmissionScore(), p::getCo2EmissionScore, p::setCo2EmissionScore);
        set(r.shippingMethod(), p::getShippingMethod, p::setShippingMethod);
        set(r.isHandmade(), p::getHandmade, p::setHandmade);
        set(r.localPickup(), p::getLocalPickup, p::setLocalPickup);
        p.refreshDeliveryEmission();
    }

    /** Text: null keeps the value, blank clears it; blank and missing count as the same value. */
    private static boolean setText(String value, Supplier<String> current, Consumer<String> setter) {
        if (value == null) {
            return false;
        }
        String next = value.isBlank() ? null : value.trim();
        String now = current.get() == null || current.get().isBlank() ? null : current.get();
        if (Objects.equals(next, now)) {
            return false;
        }
        setter.accept(next);
        return true;
    }

    private static long cents(java.math.BigDecimal amount) {
        try {
            return com.wovely.common.money.Money.centsOf(amount);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("price: " + e.getMessage());
        }
    }

    private static <T> boolean set(T value, Supplier<T> current, Consumer<T> setter) {
        if (value == null || Objects.equals(value, current.get())) {
            return false;
        }
        setter.accept(value);
        return true;
    }
}
