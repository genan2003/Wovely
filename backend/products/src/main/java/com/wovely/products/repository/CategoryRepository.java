package com.wovely.products.repository;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import com.wovely.products.models.Category;

public interface CategoryRepository extends MongoRepository<Category, String> {
  List<Category> findByParentId(String parentId);
  List<Category> findByLevel(Integer level);
  Category findBySlug(String slug);
}
