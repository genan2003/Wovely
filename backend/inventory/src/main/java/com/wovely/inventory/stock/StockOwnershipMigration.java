package com.wovely.inventory.stock;

import java.util.Date;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.wovely.inventory.models.InventoryItem;

/**
 * One-off migration for PLAN.md 4.2 / ADR 0001: until now {@code products.stockQuantity} was the truth
 * and inventory held a lossy copy. Copy every product's current level into inventory once, then
 * inventory owns it. Reads the products collection directly (shared database) — only here, only once,
 * guarded by a marker in {@code migrations}.
 */
@Component
@Order(0)
public class StockOwnershipMigration implements ApplicationRunner {

    static final String MARKER = "stock-ownership-v1";
    private static final Logger logger = LoggerFactory.getLogger(StockOwnershipMigration.class);

    private final MongoTemplate mongo;

    public StockOwnershipMigration(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrate();
    }

    /** Listings carry {@code priceCents} since PLAN.md 7.5; older ones still have a double {@code price}. */
    static Long priceCents(Document p) {
        Number cents = p.get("priceCents", Number.class);
        if (cents != null) {
            return cents.longValue();
        }
        Number legacy = p.get("price", Number.class);
        return legacy == null ? null : com.wovely.common.money.Money.legacyCents(legacy);
    }

    public int migrate() {
        if (mongo.exists(new Query(Criteria.where("_id").is(MARKER)), "migrations")) {
            return 0;
        }
        int count = 0;
        for (Document p : mongo.getCollection("products").find()) {
            Object id = p.get("_id");
            String sellerId = p.getString("sellerId");
            if (id == null || sellerId == null) {
                continue;
            }
            Number stock = p.get("stockQuantity", Number.class);
            Number threshold = p.get("lowStockThreshold", Number.class);
            Update u = new Update()
                    .set("stockQuantity", stock == null ? 0 : Math.max(0, stock.intValue()))
                    .set("lowStockThreshold", threshold == null ? 5 : threshold.intValue())
                    .set("productName", p.getString("name"))
                    .set("priceCents", priceCents(p))
                    .set("imageUrl", p.getString("imageUrl"))
                    .set("category", p.getString("category"))
                    .set("isActive", !"ARCHIVED".equals(p.getString("status")))
                    .setOnInsert("createdAt", new Date())
                    .set("updatedAt", new Date())
                    .inc("version", 1)
                    .set("outboxPending", true);
            mongo.upsert(new Query(Criteria.where("productId").is(id.toString()).and("sellerId").is(sellerId)), u,
                    InventoryItem.class);
            count++;
        }
        mongo.getCollection("migrations").insertOne(new Document("_id", MARKER).append("at", new Date()).append("items", count));
        logger.info("Stock ownership migration: {} products copied into inventory", count);
        return count;
    }
}
