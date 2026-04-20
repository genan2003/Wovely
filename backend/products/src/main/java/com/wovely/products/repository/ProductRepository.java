package com.wovely.products.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import com.wovely.products.models.Product;

public interface ProductRepository extends MongoRepository<Product, String> {
  List<Product> findByCategory(String category);
  List<Product> findBySellerId(String sellerId);
  List<Product> findByStatus(String status);
  List<Product> findByStatusAndCategory(String status, String category);
  List<Product> findBySellerIdAndCategory(String sellerId, String category);
  Optional<Product> findBySellerIdAndId(String sellerId, String id);
}
