package com.wovely.inventory.stock;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.util.Date;
import java.util.Optional;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.wovely.common.error.ApiException;
import com.wovely.inventory.models.InventoryItem;

/**
 * The only code that changes stock (PLAN.md 4.2, ADR 0001). Every operation is one atomic
 * {@code findAndModify} on the item document that also bumps {@code version} and sets
 * {@code outboxPending}: the change and its "must be published" mark can't diverge, even without
 * multi-document transactions. {@link StockEventRelay} publishes and clears the mark.
 */
@Service
public class StockLedger {

    private final MongoTemplate mongo;

    public StockLedger(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Product details shown in the seller's inventory views (not authoritative for anything). */
    public record Details(String productName, Double price, String imageUrl, String category, String co2EmissionScore,
            String shippingMethod, Boolean isHandmade) {
    }

    /**
     * Registers a product's stock (idempotent). The initial quantity applies only when the item is
     * created; afterwards only details and the threshold are refreshed.
     */
    public InventoryItem init(String productId, String sellerId, int initialQuantity, Integer lowStockThreshold,
            Details d) {
        if (initialQuantity < 0) {
            throw ApiException.badRequest("initialQuantity must not be negative");
        }
        Update u = changed(new Update())
                .setOnInsert("stockQuantity", initialQuantity)
                .setOnInsert("createdAt", new Date())
                .set("isActive", true);
        if (lowStockThreshold != null) {
            u.set("lowStockThreshold", Math.max(0, lowStockThreshold));
        } else {
            u.setOnInsert("lowStockThreshold", 5);
        }
        if (d != null) {
            u.set("productName", d.productName()).set("price", d.price()).set("imageUrl", d.imageUrl())
                    .set("category", d.category()).set("co2EmissionScore", d.co2EmissionScore())
                    .set("shippingMethod", d.shippingMethod()).set("isHandmade", d.isHandmade());
        }
        return mongo.findAndModify(item(productId, sellerId), u,
                FindAndModifyOptions.options().upsert(true).returnNew(true), InventoryItem.class);
    }

    /** Takes stock for an order; 409 when not enough is left, 404 for an unknown/inactive item. */
    public InventoryItem reserve(String productId, String sellerId, int quantity) {
        int qty = positive(quantity);
        InventoryItem updated = mongo.findAndModify(
                new Query(activeItem(productId, sellerId).and("stockQuantity").gte(qty)),
                changed(new Update().inc("stockQuantity", -qty)),
                FindAndModifyOptions.options().returnNew(true), InventoryItem.class);
        if (updated == null) {
            InventoryItem current = find(productId, sellerId)
                    .filter(i -> !Boolean.FALSE.equals(i.getIsActive()))
                    .orElseThrow(() -> ApiException.notFound("No stock record for product " + productId));
            throw ApiException.conflict("Insufficient stock for product " + productId + " (available: "
                    + current.getStockQuantity() + ", requested: " + qty + ")");
        }
        return updated;
    }

    /** Gives reserved stock back (cancel, refund, return, order edit). Works for archived items too. */
    public InventoryItem release(String productId, String sellerId, int quantity) {
        return apply(productId, sellerId, changed(new Update().inc("stockQuantity", positive(quantity))));
    }

    /** Seller adds new units. */
    public InventoryItem restock(String productId, String sellerId, int quantity) {
        return apply(productId, sellerId,
                changed(new Update().inc("stockQuantity", positive(quantity)).set("lastRestockedAt", new Date())));
    }

    /** Seller sets the absolute level (stock count). */
    public InventoryItem setLevel(String productId, String sellerId, int quantity) {
        if (quantity < 0) {
            throw ApiException.badRequest("quantity must not be negative");
        }
        return apply(productId, sellerId, changed(new Update().set("stockQuantity", quantity)));
    }

    /** Listing archived: keep the record (returns may still release stock) but stop selling it. */
    public InventoryItem deactivate(String productId, String sellerId) {
        return apply(productId, sellerId, changed(new Update().set("isActive", false)));
    }

    public Optional<InventoryItem> find(String productId, String sellerId) {
        return Optional.ofNullable(mongo.findOne(item(productId, sellerId), InventoryItem.class));
    }

    private InventoryItem apply(String productId, String sellerId, Update u) {
        InventoryItem updated = mongo.findAndModify(item(productId, sellerId), u,
                FindAndModifyOptions.options().returnNew(true), InventoryItem.class);
        if (updated == null) {
            throw ApiException.notFound("No stock record for product " + productId);
        }
        return updated;
    }

    private static Query item(String productId, String sellerId) {
        return query(where("productId").is(productId).and("sellerId").is(sellerId));
    }

    private static Criteria activeItem(String productId, String sellerId) {
        return where("productId").is(productId).and("sellerId").is(sellerId).and("isActive").ne(false);
    }

    /** Every change: new version, pending publication. */
    private static Update changed(Update u) {
        return u.inc("version", 1).set("outboxPending", true).set("updatedAt", new Date());
    }

    private static int positive(int quantity) {
        if (quantity <= 0) {
            throw ApiException.badRequest("Valid quantity is required");
        }
        return quantity;
    }
}
