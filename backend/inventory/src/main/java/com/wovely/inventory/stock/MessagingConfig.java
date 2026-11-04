package com.wovely.inventory.stock;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.wovely.common.events.StockChanged;

/** Inventory owns the {@value StockChanged#EXCHANGE} exchange; consumers declare their own queues. */
@Configuration
@EnableScheduling
public class MessagingConfig {

    @Bean
    TopicExchange inventoryExchange() {
        return new TopicExchange(StockChanged.EXCHANGE, true, false);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
