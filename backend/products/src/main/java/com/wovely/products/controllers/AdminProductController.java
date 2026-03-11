package com.wovely.products.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.wovely.products.models.Product;
import com.wovely.products.repository.ProductRepository;
import java.util.List;
import java.util.Optional;

@CrossOrigin(origins = "http://localhost:4200", maxAge = 3600)
@RestController
@RequestMapping("/api/products/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

  @Autowired
  ProductRepository productRepository;

  @GetMapping("/pending")
  public ResponseEntity<List<Product>> getPendingProducts() {
    try {
      List<Product> products = productRepository.findByStatus("PENDING");
      if (products.isEmpty()) {
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
      }
      return new ResponseEntity<>(products, HttpStatus.OK);
    } catch (Exception e) {
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @GetMapping("/seller/{sellerId}")
  public ResponseEntity<List<Product>> getProductsBySeller(@PathVariable("sellerId") String sellerId) {
    try {
      List<Product> products = productRepository.findBySellerId(sellerId);
      if (products.isEmpty()) {
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
      }
      return new ResponseEntity<>(products, HttpStatus.OK);
    } catch (Exception e) {
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @PutMapping("/{id}/status")
  public ResponseEntity<Product> updateProductStatus(@PathVariable("id") String id, @RequestBody java.util.Map<String, String> body) {
    Optional<Product> productData = productRepository.findById(id);

    if (productData.isPresent()) {
      Product product = productData.get();
      String newStatus = body.get("status");
      if (newStatus != null) {
        product.setStatus(newStatus);
        return new ResponseEntity<>(productRepository.save(product), HttpStatus.OK);
      }
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    } else {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
  }

  @PutMapping("/{id}")
  public ResponseEntity<Product> editProductContent(@PathVariable("id") String id, @RequestBody Product product) {
    Optional<Product> productData = productRepository.findById(id);

    if (productData.isPresent()) {
      Product _product = productData.get();
      _product.setName(product.getName());
      _product.setDescription(product.getDescription());
      _product.setPrice(product.getPrice());
      _product.setImageUrl(product.getImageUrl());
      _product.setCategory(product.getCategory());
      _product.setCo2EmissionScore(product.getCo2EmissionScore());
      _product.setShippingMethod(product.getShippingMethod());
      _product.setHandmade(product.isHandmade());
      // We do not change sellerId
      
      return new ResponseEntity<>(productRepository.save(_product), HttpStatus.OK);
    } else {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
  }
}
