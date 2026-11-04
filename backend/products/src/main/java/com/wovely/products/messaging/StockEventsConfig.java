package com.wovely.products.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.wovely.common.events.StockChanged;

/**
 * Products' subscription to inventory's stock events (PLAN.md 4.2): a durable quorum queue bound to
 * {@value StockChanged#EXCHANGE}; messages that keep failing go to a dead-letter queue instead of
 * blocking the queue. Declarations are idempotent, so either service can start first.
 */
@Configuration
public class StockEventsConfig {

    public static final String QUEUE = "products.stock-changed";
    public static final String DLQ = "products.stock-changed.dlq";

    @Bean
    TopicExchange inventoryExchange() {
        return new TopicExchange(StockChanged.EXCHANGE, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(StockChanged.DLX, true, false);
    }

    @Bean
    Queue stockChangedQueue() {
        return QueueBuilder.durable(QUEUE).quorum()
                .deadLetterExchange(StockChanged.DLX).deadLetterRoutingKey(DLQ).build();
    }

    @Bean
    Queue stockChangedDlq() {
        return QueueBuilder.durable(DLQ).quorum().build();
    }

    @Bean
    Binding stockChangedBinding() {
        return BindingBuilder.bind(stockChangedQueue()).to(inventoryExchange()).with(StockChanged.ROUTING_KEY);
    }

    @Bean
    Binding dlqBinding() {
        return BindingBuilder.bind(stockChangedDlq()).to(deadLetterExchange()).with(DLQ);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
