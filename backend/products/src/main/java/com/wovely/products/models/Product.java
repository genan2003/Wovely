package com.wovely.products.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "products")
public class Product {
  @Id
  private String id;
  
  private String name;
  private String description;
  private double price;
  private String sellerId;
  private String imageUrl;
  private String category;
  private String co2EmissionScore; // Low, Medium, High
  private String shippingMethod; // e.g., Carbon Neutral Courier
  private boolean isHandmade;
  private String status = "PENDING"; // PENDING, APPROVED, REJECTED, NEEDS_CHANGES

  public Product() {
  }

  public Product(String name, String description, double price, String sellerId, String imageUrl, String category, String co2EmissionScore, String shippingMethod, boolean isHandmade) {
    this.name = name;
    this.description = description;
    this.price = price;
    this.sellerId = sellerId;
    this.imageUrl = imageUrl;
    this.category = category;
    this.co2EmissionScore = co2EmissionScore;
    this.shippingMethod = shippingMethod;
    this.isHandmade = isHandmade;
    this.status = "PENDING";
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public double getPrice() {
    return price;
  }

  public void setPrice(double price) {
    this.price = price;
  }

  public String getSellerId() {
    return sellerId;
  }

  public void setSellerId(String sellerId) {
    this.sellerId = sellerId;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getCo2EmissionScore() {
    return co2EmissionScore;
  }

  public void setCo2EmissionScore(String co2EmissionScore) {
    this.co2EmissionScore = co2EmissionScore;
  }

  public String getShippingMethod() {
    return shippingMethod;
  }

  public void setShippingMethod(String shippingMethod) {
    this.shippingMethod = shippingMethod;
  }

  public boolean isHandmade() {
    return isHandmade;
  }

  public void setHandmade(boolean handmade) {
    isHandmade = handmade;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
