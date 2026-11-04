package com.wovely.inventory.stock;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.wovely.common.events.StockChanged;
import com.wovely.inventory.models.InventoryItem;

/**
 * Outbox relay (PLAN.md 4.2): publishes the current level of every item marked {@code outboxPending}
 * with publisher confirms, then clears the mark only if the version is still the one published — a
 * change that happened meanwhile stays pending and goes out next round. At-least-once delivery;
 * consumers dedupe by version.
 */
@Component
public class StockEventRelay {

    private static final Logger logger = LoggerFactory.getLogger(StockEventRelay.class);
    static final int BATCH = 200;

    private final MongoTemplate mongo;
    private final RabbitTemplate rabbit;

    public StockEventRelay(MongoTemplate mongo, RabbitTemplate rabbit) {
        this.mongo = mongo;
        this.rabbit = rabbit;
    }

    @Scheduled(fixedDelayString = "${inventory.outbox.poll-ms:250}")
    public void publishPending() {
        List<InventoryItem> pending = mongo.find(new Query(where("outboxPending").is(true))
                .with(Sort.by("updatedAt")).limit(BATCH), InventoryItem.class);
        for (InventoryItem item : pending) {
            try {
                publish(item);
            } catch (RuntimeException e) {
                // Broker down or unconfirmed: leave it pending and retry on the next run.
                logger.warn("Stock event for product {} v{} not published yet: {}", item.getProductId(),
                        item.getVersion(), e.getMessage());
                return;
            }
        }
    }

    void publish(InventoryItem item) {
        long version = item.getVersion() == null ? 0 : item.getVersion();
        StockChanged event = new StockChanged(item.getProductId(), item.getSellerId(),
                item.getStockQuantity() == null ? 0 : item.getStockQuantity(),
                item.getLowStockThreshold() == null ? 5 : item.getLowStockThreshold(),
                !Boolean.FALSE.equals(item.getIsActive()), version, Instant.now());
        rabbit.invoke(ops -> {
            ops.convertAndSend(StockChanged.EXCHANGE, StockChanged.ROUTING_KEY, event, m -> {
                m.getMessageProperties().setMessageId(item.getProductId() + ":" + version);
                return m;
            });
            ops.waitForConfirmsOrDie(5_000);
            return null;
        });
        mongo.updateFirst(new Query(where("_id").is(item.getId()).and("version").is(item.getVersion())),
                new Update().set("outboxPending", false), InventoryItem.class);
    }
}
