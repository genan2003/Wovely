package com.wovely.products.services;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.wovely.products.models.Product;

/**
 * One-off, idempotent migration: listings saved before PLAN.md 3.5 have no {@code deliveryEmission};
 * derive it once at startup so the catalog filter can query the field directly.
 */
@Component
public class DeliveryEmissionBackfill implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryEmissionBackfill.class);

    private final MongoTemplate mongo;

    public DeliveryEmissionBackfill(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments args) {
        backfill();
    }

    /** @return number of listings updated */
    public int backfill() {
        Query missing = new Query(where("deliveryEmission").exists(false));
        int updated = 0;
        for (Product p : mongo.find(missing, Product.class)) {
            p.refreshDeliveryEmission();
            mongo.updateFirst(new Query(where("_id").is(p.getId())),
                    new Update().set("deliveryEmission", p.getDeliveryEmission()), Product.class);
            updated++;
        }
        if (updated > 0) {
            logger.info("Derived deliveryEmission for {} existing listings", updated);
        }
        return updated;
    }
}
