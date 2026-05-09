package com.wovely.products.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "reviews")
public class Review {
  @Id
  private String id;
  private String productId;
  private String userId;
  private String username;
  private int rating; // 1 to 5
  private String comment;
  private List<String> photoUrls;
  private LocalDateTime createdAt;

  public Review() {
    this.createdAt = LocalDateTime.now();
  }

  public Review(String productId, String userId, String username, int rating, String comment, List<String> photoUrls) {
    this.productId = productId;
    this.userId = userId;
    this.username = username;
    this.rating = rating;
    this.comment = comment;
    this.photoUrls = photoUrls;
    this.createdAt = LocalDateTime.now();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getProductId() {
    return productId;
  }

  public void setProductId(String productId) {
    this.productId = productId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public int getRating() {
    return rating;
  }

  public void setRating(int rating) {
    this.rating = rating;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public List<String> getPhotoUrls() {
    return photoUrls;
  }

  public void setPhotoUrls(List<String> photoUrls) {
    this.photoUrls = photoUrls;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
