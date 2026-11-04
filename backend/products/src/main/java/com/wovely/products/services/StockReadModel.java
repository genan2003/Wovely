package com.wovely.products.services;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.mongodb.client.result.UpdateResult;
import com.wovely.products.models.Product;

/**
 * Products' copy of stock (PLAN.md 4.2). Written only from inventory's answers and events, and only
 * forwards in time: an update applies when its version is newer than the stored {@code stockVersion},
 * so redelivered or out-of-order events are no-ops.
 */
@Service
public class StockReadModel {

    private final MongoTemplate mongo;

    public StockReadModel(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** @return true if this version was newer and got applied */
    public boolean apply(String productId, int stockQuantity, Integer lowStockThreshold, long version) {
        Query q = new Query(where("_id").is(productId).orOperator(
                where("stockVersion").exists(false), where("stockVersion").is(null), where("stockVersion").lt(version)));
        Update u = new Update().set("stockQuantity", Math.max(0, stockQuantity)).set("stockVersion", version);
        if (lowStockThreshold != null) {
            u.set("lowStockThreshold", lowStockThreshold);
        }
        UpdateResult r = mongo.updateFirst(q, u, Product.class);
        return r.getModifiedCount() > 0;
    }

    static Criteria byId(String id) {
        return where("_id").is(id);
    }
}
