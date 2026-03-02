package com.wovely.products.repository;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import com.wovely.products.models.Product;

public interface ProductRepository extends MongoRepository<Product, String> {
  List<Product> findByCategory(String category);
  List<Product> findBySellerId(String sellerId);
}
