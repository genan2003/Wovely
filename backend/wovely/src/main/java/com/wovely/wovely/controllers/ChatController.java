package com.wovely.wovely.controllers;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wovely.wovely.exception.ApiException;
import com.wovely.wovely.models.ChatMessage;
import com.wovely.wovely.repository.ChatMessageRepository;
import com.wovely.wovely.repository.UserRepository;
import com.wovely.wovely.security.services.UserDetailsImpl;

/**
 * Buyer–seller messaging. The sender is always the authenticated caller and you can only read
 * conversations you take part in. Admins read chats only through reports (ReportController).
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    ChatMessageRepository chatMessageRepository;

    @Autowired
    UserRepository userRepository;

    /** Conversation between the caller ({@code user1}, must be you) and {@code user2}; marks received messages read. */
    @GetMapping("/conversation")
    public List<ChatMessage> getConversation(@RequestParam String user1, @RequestParam String user2,
            @AuthenticationPrincipal UserDetailsImpl me) {
        if (!me.getId().equals(user1)) {
            throw ApiException.forbidden("You can only read your own conversations");
        }
        List<ChatMessage> messages = new java.util.ArrayList<>(chatMessageRepository.findConversationLogs(user1, user2));
        messages.sort((m1, m2) -> m1.getTimestamp().compareTo(m2.getTimestamp()));
        messages.stream()
            .filter(m -> m.getReceiverId().equals(me.getId()) && !m.isRead())
            .forEach(m -> {
                m.setRead(true);
                chatMessageRepository.save(m);
            });
        return messages;
    }

    /** Send a message as the authenticated user ({@code senderId} in the body is ignored). */
    @PostMapping("/send")
    public ResponseEntity<ChatMessage> sendMessage(@RequestBody ChatMessage message,
            @AuthenticationPrincipal UserDetailsImpl me) {
        if (message.getReceiverId() == null || message.getContent() == null || message.getContent().isBlank()) {
            throw ApiException.badRequest("receiverId and content are required");
        }
        if (message.getReceiverId().equals(me.getId())) {
            throw ApiException.badRequest("You cannot message yourself");
        }
        if (!userRepository.existsById(message.getReceiverId())) {
            throw ApiException.badRequest("Receiver not found");
        }
        ChatMessage saved = chatMessageRepository.save(new ChatMessage(
            me.getId(),
            message.getReceiverId(),
            message.getProductId(),
            message.getProductThumbnail(),
            message.getContent()));
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    @GetMapping("/unread/{userId}")
    @PreAuthorize("#userId == authentication.principal.id")
    public List<ChatMessage> getUnreadMessages(@PathVariable String userId) {
        return chatMessageRepository.findByReceiverIdAndIsReadFalse(userId);
    }

    /** Latest message per conversation partner. Aggregation instead of findAll: PLAN.md 2.10. */
    @GetMapping("/conversations/{userId}")
    @PreAuthorize("#userId == authentication.principal.id")
    public Collection<ChatMessage> getConversations(@PathVariable String userId) {
        List<ChatMessage> allMessages = chatMessageRepository.findAll().stream()
            .filter(m -> userId.equals(m.getSenderId()) || userId.equals(m.getReceiverId()))
            .sorted((m1, m2) -> m2.getTimestamp().compareTo(m1.getTimestamp()))
            .toList();
        Map<String, ChatMessage> conversations = new LinkedHashMap<>();
        for (ChatMessage m : allMessages) {
            String otherId = m.getSenderId().equals(userId) ? m.getReceiverId() : m.getSenderId();
            conversations.putIfAbsent(otherId, m);
        }
        return conversations.values();
    }
}
