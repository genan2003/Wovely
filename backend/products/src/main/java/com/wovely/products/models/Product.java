package com.wovely.products.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "products")
@CompoundIndex(name = "status_category", def = "{'status': 1, 'category': 1}")
@CompoundIndex(name = "seller_status", def = "{'sellerId': 1, 'status': 1}")
public class Product {

  /** Moderation/lifecycle states. ARCHIVED = soft-deleted by the seller (PLAN.md 2.8). */
  public static final java.util.Set<String> STATUSES = java.util.Set.of("PENDING", "APPROVED", "REJECTED", "NEEDS_CHANGES", "ARCHIVED");
  public static final String ARCHIVED = "ARCHIVED";

  @Id
  private String id;

  private String name;
  private String description;
  private Double price;
  private String sellerId;
  private String imageUrl;
  private String category;
  private String categoryPath; // e.g., "Home & Living > Ceramics > Mugs"
  private java.util.List<String> materials;
  private String city;
  private String region;
  private Double latitude;
  private Double longitude;
  private String co2EmissionScore; // Low, Medium, High
  private String shippingMethod; // e.g., Carbon Neutral Courier
  private Boolean isHandmade;
  /** Buyers may collect the item from the maker (counts as zero-emission delivery). */
  private Boolean localPickup;
  /** Server-derived from shipping method, CO₂ score and local pickup — see {@link #refreshDeliveryEmission()}. */
  @Indexed
  @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
  private EDeliveryEmission deliveryEmission;
  @Indexed
  private String status = "PENDING"; // see STATUSES
  
  // Inventory tracking fields
  // No field defaults: a missing value in a request body must stay null, otherwise partial updates
  // silently reset stock to 0 (bug found by the Postman run, see ProductSecurityTest).
  private Integer stockQuantity;
  private Integer lowStockThreshold;
  /** Read model (PLAN.md 4.2): last inventory version applied to stockQuantity/lowStockThreshold. */
  @com.fasterxml.jackson.annotation.JsonIgnore
  private Long stockVersion;

  public static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;

  /**
   * Jackson must bind request bodies through this constructor. Jackson 3 would otherwise pick the
   * all-args constructor below (parameter names are compiled in) and its null → 0/5 defaults turned
   * every partial update into "stock = 0".
   */
  @com.fasterxml.jackson.annotation.JsonCreator
  public Product() {
  }

  public Product(String name, String description, Double price, String sellerId, String imageUrl, String category, String categoryPath, java.util.List<String> materials, String city, String region, Double latitude, Double longitude, String co2EmissionScore, String shippingMethod, Boolean isHandmade, Integer stockQuantity, Integer lowStockThreshold) {
    this.name = name;
    this.description = description;
    this.price = price;
    this.sellerId = sellerId;
    this.imageUrl = imageUrl;
    this.category = category;
    this.categoryPath = categoryPath;
    this.materials = materials;
    this.city = city;
    this.region = region;
    this.latitude = latitude;
    this.longitude = longitude;
    this.co2EmissionScore = co2EmissionScore;
    this.shippingMethod = shippingMethod;
    this.isHandmade = isHandmade;
    this.stockQuantity = stockQuantity != null ? stockQuantity : 0;
    this.lowStockThreshold = lowStockThreshold != null ? lowStockThreshold : 5;
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

  public Double getPrice() {
    return price;
  }

  public void setPrice(Double price) {
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

  public String getCategoryPath() {
    return categoryPath;
  }

  public void setCategoryPath(String categoryPath) {
    this.categoryPath = categoryPath;
  }

  public java.util.List<String> getMaterials() {
    return materials;
  }

  public void setMaterials(java.util.List<String> materials) {
    this.materials = materials;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public Double getLatitude() {
    return latitude;
  }

  public void setLatitude(Double latitude) {
    this.latitude = latitude;
  }

  public Double getLongitude() {
    return longitude;
  }

  public void setLongitude(Double longitude) {
    this.longitude = longitude;
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

  public Boolean getHandmade() {
    return isHandmade;
  }

  public void setHandmade(Boolean handmade) {
    isHandmade = handmade;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Long getStockVersion() {
    return stockVersion;
  }

  public void setStockVersion(Long stockVersion) {
    this.stockVersion = stockVersion;
  }

  public Boolean getLocalPickup() {
    return localPickup;
  }

  public void setLocalPickup(Boolean localPickup) {
    this.localPickup = localPickup;
  }

  public EDeliveryEmission getDeliveryEmission() {
    return deliveryEmission;
  }

  /** Read-only in JSON: the level is always recomputed (PLAN.md 3.5). */
  public void setDeliveryEmission(EDeliveryEmission deliveryEmission) {
    this.deliveryEmission = deliveryEmission;
  }

  /** Recompute {@link #deliveryEmission}; call after any change to shipping, CO₂ score or pickup. */
  public Product refreshDeliveryEmission() {
    this.deliveryEmission = EDeliveryEmission.classify(shippingMethod, co2EmissionScore, localPickup);
    return this;
  }

  public Integer getStockQuantity() {
    return stockQuantity;
  }

  public void setStockQuantity(Integer stockQuantity) {
    this.stockQuantity = stockQuantity;
  }

  public Integer getLowStockThreshold() {
    return lowStockThreshold;
  }

  public void setLowStockThreshold(Integer lowStockThreshold) {
    this.lowStockThreshold = lowStockThreshold;
  }

  public boolean isLowStock() {
    return stockQuantity != null && stockQuantity <= (lowStockThreshold != null ? lowStockThreshold : DEFAULT_LOW_STOCK_THRESHOLD);
  }

  public boolean isOutOfStock() {
    return stockQuantity == null || stockQuantity == 0;
  }
}
