package com.wovely.wovely.repository;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import com.wovely.wovely.models.ChatMessage;

import org.springframework.data.mongodb.repository.Query;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
    @Query("{$or: [{ 'senderId': ?0, 'receiverId': ?1 }, { 'senderId': ?1, 'receiverId': ?0 }]}")
    List<ChatMessage> findConversationLogs(String user1, String user2);
    
    List<ChatMessage> findByReceiverIdAndIsReadFalse(String receiverId);
}
