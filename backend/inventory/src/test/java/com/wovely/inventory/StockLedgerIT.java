package com.wovely.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.wovely.common.error.ApiException;
import com.wovely.inventory.models.InventoryItem;
import com.wovely.inventory.stock.StockLedger;
import com.wovely.inventory.support.AbstractMongoIT;

/** PLAN.md 4.2 — inventory is the single, atomic writer of stock (real MongoDB). */
class StockLedgerIT extends AbstractMongoIT {

    @Autowired StockLedger ledger;
    @Autowired org.springframework.data.mongodb.core.MongoTemplate mongo;
    @Autowired com.wovely.inventory.stock.PriceCentsBackfill priceBackfill;

    private final String product = "p-" + UUID.randomUUID();
    private final String seller = "s-" + UUID.randomUUID();

    private InventoryItem init(int qty) {
        return ledger.init(product, seller, qty, 2, new StockLedger.Details("Basket", 1000L, null, "Home", "Low", "Bike", true));
    }

    @Test
    void initIsIdempotentAndOnlySetsQuantityOnCreate() {
        InventoryItem first = init(5);
        assertThat(first.getStockQuantity()).isEqualTo(5);
        assertThat(first.getVersion()).isEqualTo(1);
        assertThat(first.getOutboxPending()).isTrue();
        InventoryItem again = init(999);
        assertThat(again.getStockQuantity()).as("existing level is kept").isEqualTo(5);
        assertThat(again.getVersion()).isEqualTo(2);
        assertThat(again.getId()).isEqualTo(first.getId());
    }

    @Test
    void everyChangeBumpsTheVersionAndMarksTheOutbox() {
        init(5);
        assertThat(ledger.reserve(product, seller, 2)).satisfies(i -> {
            assertThat(i.getStockQuantity()).isEqualTo(3);
            assertThat(i.getVersion()).isEqualTo(2);
        });
        assertThat(ledger.release(product, seller, 1).getStockQuantity()).isEqualTo(4);
        assertThat(ledger.restock(product, seller, 6).getStockQuantity()).isEqualTo(10);
        InventoryItem last = ledger.setLevel(product, seller, 7);
        assertThat(last.getStockQuantity()).isEqualTo(7);
        assertThat(last.getVersion()).isEqualTo(5);
        assertThat(last.getOutboxPending()).isTrue();
    }

    @Test
    void reserveGuards() {
        init(2);
        assertThatThrownBy(() -> ledger.reserve(product, seller, 3)).isInstanceOf(ApiException.class)
            .hasMessageContaining("Insufficient").extracting("status").hasToString("409 CONFLICT");
        assertThatThrownBy(() -> ledger.reserve(product, "other-seller", 1)).extracting("status").hasToString("404 NOT_FOUND");
        assertThatThrownBy(() -> ledger.reserve("nope", seller, 1)).extracting("status").hasToString("404 NOT_FOUND");
        assertThatThrownBy(() -> ledger.reserve(product, seller, 0)).extracting("status").hasToString("400 BAD_REQUEST");

        ledger.deactivate(product, seller);
        assertThatThrownBy(() -> ledger.reserve(product, seller, 1)).extracting("status").hasToString("404 NOT_FOUND");
        assertThat(ledger.release(product, seller, 1).getStockQuantity()).as("returns still restock archived items").isEqualTo(3);
    }

    @Test
    void concurrentReservationsNeverOversell() throws Exception {
        init(5);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        for (int i = 0; i < 40; i++) {
            pool.submit(() -> {
                try {
                    go.await();
                    ledger.reserve(product, seller, 1);
                    ok.incrementAndGet();
                } catch (Exception ignored) {
                }
            });
        }
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(ok.get()).isEqualTo(5);
        assertThat(ledger.find(product, seller).orElseThrow().getStockQuantity()).isZero();
    }

    // ---------------------------------------------------------------- PLAN.md 7.5 money as cents

    private org.bson.Document raw(String productId) {
        return mongo.getCollection("inventory_items").find(new org.bson.Document("productId", productId)).first();
    }

    @Test
    void registrationStoresThePriceInCents() {
        init(1);
        assertThat(raw(product)).containsEntry("priceCents", 1000L).doesNotContainKey("price");
    }

    @Test
    void legacyDoublePricesAreConvertedOnce() {
        String legacy = "p-legacy-" + UUID.randomUUID();
        mongo.getCollection("inventory_items").insertOne(new org.bson.Document("productId", legacy)
                .append("sellerId", seller).append("price", 42.5).append("stockQuantity", 3));
        assertThat(priceBackfill.backfill()).isGreaterThanOrEqualTo(1);
        assertThat(raw(legacy)).containsEntry("priceCents", 4250L).doesNotContainKey("price");
        assertThat(priceBackfill.backfill()).isZero();
    }
}
