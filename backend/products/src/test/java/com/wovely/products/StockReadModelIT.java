package com.wovely.products;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import com.wovely.common.events.StockChanged;
import com.wovely.products.messaging.StockEventsConfig;
import com.wovely.products.models.Product;
import com.wovely.products.repository.ProductRepository;
import com.wovely.products.services.StockReadModel;
import com.wovely.products.support.AbstractMongoIT;

/** PLAN.md 4.2 — the catalog's stock is a version-guarded read model fed by inventory events (real Mongo + RabbitMQ). */
class StockReadModelIT extends AbstractMongoIT {

    @Autowired StockReadModel readModel;
    @Autowired ProductRepository products;
    @Autowired RabbitTemplate rabbit;

    private Product product() {
        Product p = new Product("Basket", "d", 1000L, "seller-it", "", "Home", "Home", List.of(), "Sibiu", "Sibiu",
                null, null, "Low", "Courier", true, 0, 1);
        p.setStatus("APPROVED");
        return products.save(p);
    }

    private int stockOf(String id) {
        return products.findById(id).orElseThrow().getStockQuantity();
    }

    private void awaitStock(String id, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline && stockOf(id) != expected) {
            Thread.sleep(50);
        }
        assertThat(stockOf(id)).isEqualTo(expected);
    }

    @Test
    void onlyNewerVersionsApply() {
        String id = product().getId();
        assertThat(readModel.apply(id, 5, 2, 3)).isTrue();
        assertThat(readModel.apply(id, 9, 2, 2)).as("older version").isFalse();
        assertThat(readModel.apply(id, 9, 2, 3)).as("duplicate").isFalse();
        assertThat(stockOf(id)).isEqualTo(5);
        assertThat(readModel.apply(id, 4, 1, 4)).isTrue();
        assertThat(products.findById(id).orElseThrow()).satisfies(p -> {
            assertThat(p.getStockQuantity()).isEqualTo(4);
            assertThat(p.getLowStockThreshold()).isEqualTo(1);
            assertThat(p.getStockVersion()).isEqualTo(4);
        });
    }

    @Test
    void eventsFromTheExchangeUpdateTheCatalogInOrderOfVersion() throws Exception {
        String id = product().getId();
        rabbit.convertAndSend(StockChanged.EXCHANGE, StockChanged.ROUTING_KEY,
                new StockChanged(id, "seller-it", 7, 2, true, 10, Instant.now()));
        awaitStock(id, 7);
        // late, out-of-order delivery of an older event must not roll the stock back
        rabbit.convertAndSend(StockChanged.EXCHANGE, StockChanged.ROUTING_KEY,
                new StockChanged(id, "seller-it", 1, 2, true, 9, Instant.now()));
        rabbit.convertAndSend(StockChanged.EXCHANGE, StockChanged.ROUTING_KEY,
                new StockChanged(id, "seller-it", 6, 2, true, 11, Instant.now()));
        awaitStock(id, 6);
        assertThat(products.findById(id).orElseThrow().getStockVersion()).isEqualTo(11);
    }

    @Test
    void poisonMessagesEndUpInTheDeadLetterQueue() throws Exception {
        MessageProperties props = new MessageProperties();
        props.setContentType("application/json");
        rabbit.send(StockChanged.EXCHANGE, StockChanged.ROUTING_KEY, new Message("{\"productId\":null,\"version\":0}".getBytes(), props));
        Message dead = rabbit.receive(StockEventsConfig.DLQ, 15_000);
        assertThat(dead).as("rejected message is dead-lettered, not redelivered forever").isNotNull();
    }
}
