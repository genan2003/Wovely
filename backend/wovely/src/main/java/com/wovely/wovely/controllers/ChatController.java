package com.wovely.wovely.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.wovely.wovely.models.ChatMessage;
import com.wovely.wovely.repository.ChatMessageRepository;
import java.util.List;

@CrossOrigin(origins = "http://localhost:4200", maxAge = 3600)
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    ChatMessageRepository chatMessageRepository;

    @GetMapping("/conversation")
    public ResponseEntity<List<ChatMessage>> getConversation(
            @RequestParam String user1, 
            @RequestParam String user2) {
        try {
            List<ChatMessage> messages = chatMessageRepository.findConversationLogs(user1, user2);
            messages.sort((m1, m2) -> m1.getTimestamp().compareTo(m2.getTimestamp()));
            
            // Mark received messages as read
            messages.stream()
                .filter(m -> m.getReceiverId().equals(user1) && !m.isRead())
                .forEach(m -> {
                    m.setRead(true);
                    chatMessageRepository.save(m);
                });

            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/send")
    public ResponseEntity<ChatMessage> sendMessage(@RequestBody ChatMessage message) {
        try {
            ChatMessage _message = chatMessageRepository.save(new ChatMessage(
                message.getSenderId(),
                message.getReceiverId(),
                message.getProductId(),
                message.getProductThumbnail(),
                message.getContent()
            ));
            return new ResponseEntity<>(_message, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/unread/{userId}")
    public ResponseEntity<List<ChatMessage>> getUnreadMessages(@PathVariable String userId) {
        try {
            List<ChatMessage> unread = chatMessageRepository.findByReceiverIdAndIsReadFalse(userId);
            return ResponseEntity.ok(unread);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/conversations/{userId}")
    public ResponseEntity<?> getConversations(@PathVariable String userId) {
        try {
            // This is a simple implementation. In a real app, this might be a complex MongoDB aggregation.
            List<ChatMessage> allMessages = chatMessageRepository.findAll().stream()
                .filter(m -> m.getSenderId().equals(userId) || m.getReceiverId().equals(userId))
                .sorted((m1, m2) -> m2.getTimestamp().compareTo(m1.getTimestamp()))
                .collect(java.util.stream.Collectors.toList());

            java.util.Map<String, ChatMessage> conversations = new java.util.LinkedHashMap<>();
            for (ChatMessage m : allMessages) {
                String otherId = m.getSenderId().equals(userId) ? m.getReceiverId() : m.getSenderId();
                if (!conversations.containsKey(otherId)) {
                    conversations.put(otherId, m);
                }
            }
            
            return ResponseEntity.ok(conversations.values());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
