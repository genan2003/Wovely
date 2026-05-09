package com.wovely.products.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Document(collection = "categories")
public class Category {
  @Id
  private String id;
  private String name;
  private String parentId; // null for top-level
  private String slug;
  private Integer level; // 0 for root, 1 for sub, etc.

  public Category() {}

  public Category(String name, String parentId, String slug, Integer level) {
    this.name = name;
    this.parentId = parentId;
    this.slug = slug;
    this.level = level;
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

  public String getParentId() {
    return parentId;
  }

  public void setParentId(String parentId) {
    this.parentId = parentId;
  }

  public String getSlug() {
    return slug;
  }

  public void setSlug(String slug) {
    this.slug = slug;
  }

  public Integer getLevel() {
    return level;
  }

  public void setLevel(Integer level) {
    this.level = level;
  }
}
