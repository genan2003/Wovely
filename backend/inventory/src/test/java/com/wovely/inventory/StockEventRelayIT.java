package com.wovely.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.wovely.common.events.StockChanged;
import com.wovely.inventory.models.InventoryItem;
import com.wovely.inventory.stock.StockLedger;
import com.wovely.inventory.stock.StockOwnershipMigration;
import com.wovely.inventory.support.AbstractMongoIT;

/** PLAN.md 4.2 — the outbox relay publishes every change to RabbitMQ and clears the mark (real broker). */
class StockEventRelayIT extends AbstractMongoIT {

    @Autowired StockLedger ledger;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired MessageConverter converter;
    @Autowired MongoTemplate mongo;
    @Autowired StockOwnershipMigration migration;

    Queue probe;
    private final String product = "p-" + UUID.randomUUID();
    private final String seller = "s-" + UUID.randomUUID();

    @BeforeEach
    void bindProbe() {
        probe = new Queue("probe-" + UUID.randomUUID(), true, false, false); // RabbitMQ 4: no transient non-exclusive queues, no x-queue-master-locator (AnonymousQueue); deleted after the test
        admin.declareQueue(probe);
        Binding b = BindingBuilder.bind(probe).to(new TopicExchange(StockChanged.EXCHANGE)).with(StockChanged.ROUTING_KEY);
        admin.declareBinding(b);
    }

    @org.junit.jupiter.api.AfterEach
    void dropProbe() {
        admin.deleteQueue(probe.getName());
    }

    private List<StockChanged> receiveFor(String productId, int expected) {
        List<StockChanged> got = new ArrayList<>();
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(10).toMillis();
        while (System.currentTimeMillis() < deadline && got.size() < expected) {
            Message m = rabbit.receive(probe.getName(), 500);
            if (m != null) {
                StockChanged e = (StockChanged) converter.fromMessage(m);
                if (e.productId().equals(productId)) got.add(e);
            }
        }
        return got;
    }

    @Test
    void changesArePublishedWithGrowingVersionsAndTheOutboxIsCleared() {
        ledger.init(product, seller, 5, 2, null);
        List<StockChanged> first = receiveFor(product, 1);
        assertThat(first).isNotEmpty();
        ledger.reserve(product, seller, 2);

        List<StockChanged> later = receiveFor(product, 1);
        assertThat(later).isNotEmpty();
        StockChanged last = later.get(later.size() - 1);
        assertThat(last.stockQuantity()).isEqualTo(3);
        assertThat(last.version()).isGreaterThan(first.get(0).version());
        assertThat(last.sellerId()).isEqualTo(seller);
        assertThat(last.lowStockThreshold()).isEqualTo(2);

        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline
                && Boolean.TRUE.equals(ledger.find(product, seller).orElseThrow().getOutboxPending())) {
            Thread.onSpinWait();
        }
        assertThat(ledger.find(product, seller).orElseThrow().getOutboxPending()).isFalse();
    }

    @Test
    void migrationCopiesProductStockOnce() {
        String pid = UUID.randomUUID().toString();
        mongo.getCollection("migrations").deleteMany(new org.bson.Document("_id", "stock-ownership-v1"));
        mongo.getCollection("products").insertOne(new org.bson.Document("_id", pid).append("sellerId", seller)
            .append("name", "Legacy mug").append("stockQuantity", 12).append("lowStockThreshold", 3).append("status", "APPROVED"));

        assertThat(migration.migrate()).isGreaterThanOrEqualTo(1);
        InventoryItem item = mongo.findOne(new Query(Criteria.where("productId").is(pid)), InventoryItem.class);
        assertThat(item.getStockQuantity()).isEqualTo(12);
        assertThat(item.getProductName()).isEqualTo("Legacy mug");
        assertThat(item.getOutboxPending()).isTrue();
        assertThat(migration.migrate()).as("runs once").isZero();
        assertThat(receiveFor(pid, 1)).extracting(StockChanged::stockQuantity).contains(12);
    }
}
