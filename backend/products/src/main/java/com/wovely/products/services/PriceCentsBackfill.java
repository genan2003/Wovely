package com.wovely.products.services;

import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.wovely.common.money.MoneyMigration;

/**
 * One-off, idempotent migration (PLAN.md 7.5): listings saved with a double {@code price} get {@code priceCents}
 * and lose {@code price}, so the stored amount is always whole cents.
 */
@Component
public class PriceCentsBackfill implements ApplicationRunner {

    private final MongoTemplate mongo;

    public PriceCentsBackfill(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments args) {
        backfill();
    }

    /** @return number of listings rewritten */
    public int backfill() {
        return MoneyMigration.toCents(mongo, "products", Map.of("price", "priceCents"), null, Map.of());
    }
}
