package com.wovely.wovely.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.wovely.wovely.models.ChatMessage;

/** Pushes a saved chat message to both participants' {@code /user/queue/messages} (PLAN.md 3.4). */
@Component
public class ChatNotifier {

    public static final String QUEUE = "/queue/messages";
    private static final Logger logger = LoggerFactory.getLogger(ChatNotifier.class);

    private final SimpMessagingTemplate broker;

    public ChatNotifier(SimpMessagingTemplate broker) {
        this.broker = broker;
    }

    /** Best effort: the message is already stored, offline users fetch it over REST. */
    public void messageSent(ChatMessage saved) {
        try {
            broker.convertAndSendToUser(saved.getReceiverId(), QUEUE, saved);
            broker.convertAndSendToUser(saved.getSenderId(), QUEUE, saved); // sender's other tabs
        } catch (RuntimeException e) {
            logger.warn("Real-time push for message {} failed: {}", saved.getId(), e.getMessage());
        }
    }
}
