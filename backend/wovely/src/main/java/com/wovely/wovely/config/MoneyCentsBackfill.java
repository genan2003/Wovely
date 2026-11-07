package com.wovely.wovely.config;

import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.wovely.common.money.MoneyMigration;

/**
 * One-off, idempotent migration (PLAN.md 7.5): orders and return requests saved with double amounts get whole
 * cents instead — totals, delivery fee, refunded amount and every embedded item price. Each document is rewritten
 * in one guarded update, so the legacy and the cents fields never coexist.
 */
@Component
public class MoneyCentsBackfill implements ApplicationRunner {

    static final Map<String, String> ITEM = Map.of("price", "priceCents");

    private final MongoTemplate mongo;

    public MoneyCentsBackfill(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments args) {
        backfill();
    }

    /** @return documents rewritten (orders + return requests) */
    public int backfill() {
        return MoneyMigration.toCents(mongo, "orders",
                Map.of("total_amount", "total_cents", "deliveryFee", "deliveryFeeCents", "refunded_amount", "refunded_cents"),
                "items", ITEM)
                + MoneyMigration.toCents(mongo, "return_requests", Map.of("totalAmount", "totalCents"), "items", ITEM);
    }
}
