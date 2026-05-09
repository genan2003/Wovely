package com.wovely.wovely.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "chat_messages")
public class ChatMessage {
    @Id
    private String id;
    private String senderId;
    private String receiverId;
    private String productId;
    private String productThumbnail;
    private String content;
    private LocalDateTime timestamp;
    private boolean isRead;

    public ChatMessage() {
        this.timestamp = LocalDateTime.now();
        this.isRead = false;
    }

    public ChatMessage(String senderId, String receiverId, String productId, String productThumbnail, String content) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.productId = productId;
        this.productThumbnail = productThumbnail;
        this.content = content;
        this.timestamp = LocalDateTime.now();
        this.isRead = false;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductThumbnail() {
        return productThumbnail;
    }

    public void setProductThumbnail(String productThumbnail) {
        this.productThumbnail = productThumbnail;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }
}
