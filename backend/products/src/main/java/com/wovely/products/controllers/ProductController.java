package com.wovely.products.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.wovely.products.models.Product;
import com.wovely.products.repository.ProductRepository;
import java.util.List;

@CrossOrigin(origins = "http://localhost:4200", maxAge = 3600)
@RestController
@RequestMapping("/api/products")
public class ProductController {

  @Autowired
  ProductRepository productRepository;

  @GetMapping
  public ResponseEntity<List<Product>> getAllProducts(@RequestParam(required = false) String category) {
    try {
      List<Product> products;
      if (category == null) {
        products = productRepository.findByStatus("APPROVED");
      } else {
        products = productRepository.findByStatusAndCategory("APPROVED", category);
      }

      if (products.isEmpty()) {
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
      }
      return new ResponseEntity<>(products, HttpStatus.OK);
    } catch (Exception e) {
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @GetMapping("/{id}")
  public ResponseEntity<Product> getProductById(@PathVariable("id") String id) {
    return productRepository.findById(id)
        .map(product -> new ResponseEntity<>(product, HttpStatus.OK))
        .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
  }

  @PostMapping
  public ResponseEntity<Product> createProduct(@RequestBody Product product) {
    try {
      Product _product = productRepository.save(new Product(
          product.getName(), 
          product.getDescription(), 
          product.getPrice(), 
          product.getSellerId(), 
          product.getImageUrl(), 
          product.getCategory(), 
          product.getCo2EmissionScore(), 
          product.getShippingMethod(), 
          product.isHandmade()));
      return new ResponseEntity<>(_product, HttpStatus.CREATED);
    } catch (Exception e) {
      return new ResponseEntity<Product>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
