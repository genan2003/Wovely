package com.wovely.inventory.stock;

import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.wovely.common.money.MoneyMigration;

/**
 * One-off, idempotent migration (PLAN.md 7.5): inventory items saved with a double {@code price} get
 * {@code priceCents} and lose {@code price}. Runs after {@link StockOwnershipMigration}.
 */
@Component
@Order(100)
public class PriceCentsBackfill implements ApplicationRunner {

    private final MongoTemplate mongo;

    public PriceCentsBackfill(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments args) {
        backfill();
    }

    /** @return number of inventory items rewritten */
    public int backfill() {
        return MoneyMigration.toCents(mongo, "inventory_items", Map.of("price", "priceCents"), null, Map.of());
    }
}
