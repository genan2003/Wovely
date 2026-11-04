package com.wovely.products.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.wovely.common.events.StockChanged;
import com.wovely.products.services.StockReadModel;

/** Applies inventory's stock events to the catalog's read model (idempotent by version). */
@Component
public class StockChangedListener {

    private static final Logger logger = LoggerFactory.getLogger(StockChangedListener.class);

    private final StockReadModel readModel;

    public StockChangedListener(StockReadModel readModel) {
        this.readModel = readModel;
    }

    @RabbitListener(queues = StockEventsConfig.QUEUE)
    public void on(StockChanged event) {
        if (event == null || event.productId() == null || event.version() <= 0) {
            throw new AmqpRejectAndDontRequeueException("Malformed stock event: " + event);
        }
        boolean applied = readModel.apply(event.productId(), event.stockQuantity(), event.lowStockThreshold(), event.version());
        logger.debug("Stock event {} v{} -> {} ({})", event.productId(), event.version(), event.stockQuantity(),
                applied ? "applied" : "stale, ignored");
    }
}
