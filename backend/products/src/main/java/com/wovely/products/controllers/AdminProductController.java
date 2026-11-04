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

@RestController
@RequestMapping("/api/products/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

  @Autowired
  ProductRepository productRepository;

  @Autowired
  com.wovely.products.services.StockService stockService;

  @Autowired
  com.wovely.products.clients.AuditClient auditClient;

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
      if (newStatus == null || !Product.STATUSES.contains(newStatus)) {
        throw com.wovely.common.error.ApiException.badRequest("status must be one of " + Product.STATUSES);
      }
      String before = product.getStatus();
      product.setStatus(newStatus);
      Product saved = productRepository.save(product);
      stockService.afterListingSaved(saved);
      auditClient.record("PRODUCT_STATUS", id, before + " -> " + newStatus
          + (body.get("reason") != null ? ", Reason: " + body.get("reason") : ""));
      return new ResponseEntity<>(saved, HttpStatus.OK);
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
      _product.setCategoryPath(product.getCategoryPath());
      _product.setMaterials(product.getMaterials());
      _product.setCity(product.getCity());
      _product.setRegion(product.getRegion());
      _product.setLatitude(product.getLatitude());
      _product.setLongitude(product.getLongitude());
      _product.setCo2EmissionScore(product.getCo2EmissionScore());
      _product.setShippingMethod(product.getShippingMethod());
      _product.setHandmade(product.getHandmade());
      if (product.getLocalPickup() != null) {
        _product.setLocalPickup(product.getLocalPickup());
      }
      _product.refreshDeliveryEmission();
      if (product.getLowStockThreshold() != null) {
        _product.setLowStockThreshold(product.getLowStockThreshold());
      }
      // We do not change sellerId
      
      Product saved = productRepository.save(_product);
      stockService.afterListingSaved(saved);
      if (product.getStockQuantity() != null) {
        saved = stockService.setLevel(saved.getSellerId(), id, Math.max(0, product.getStockQuantity()));
      }
      auditClient.record("PRODUCT_EDIT_BY_ADMIN", id, "Listing content edited by admin");
      
      return new ResponseEntity<>(saved, HttpStatus.OK);
    } else {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
  }
}
