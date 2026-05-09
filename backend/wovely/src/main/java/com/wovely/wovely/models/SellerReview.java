package com.wovely.wovely.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "seller_reviews")
public class SellerReview {
    @Id
    private String id;
    private String sellerId;
    private String buyerId;
    private String buyerName;
    private int overallRating;
    private int communicationRating;
    private int shippingRating;
    private String comment;
    private LocalDateTime createdAt;

    public SellerReview() {
        this.createdAt = LocalDateTime.now();
    }

    public SellerReview(String sellerId, String buyerId, String buyerName, 
                        int overallRating, int communicationRating, int shippingRating, String comment) {
        this.sellerId = sellerId;
        this.buyerId = buyerId;
        this.buyerName = buyerName;
        this.overallRating = overallRating;
        this.communicationRating = communicationRating;
        this.shippingRating = shippingRating;
        this.comment = comment;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }

    public String getBuyerId() { return buyerId; }
    public void setBuyerId(String buyerId) { this.buyerId = buyerId; }

    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }

    public int getOverallRating() { return overallRating; }
    public void setOverallRating(int overallRating) { this.overallRating = overallRating; }

    public int getCommunicationRating() { return communicationRating; }
    public void setCommunicationRating(int communicationRating) { this.communicationRating = communicationRating; }

    public int getShippingRating() { return shippingRating; }
    public void setShippingRating(int shippingRating) { this.shippingRating = shippingRating; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
