package com.wovely.products.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.wovely.products.models.Product;
import com.wovely.products.repository.ProductRepository;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

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

  /**
   * Get all products by seller ID (including non-approved ones for inventory management).
   */
  @GetMapping("/seller/{sellerId}")
  @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
  public ResponseEntity<?> getProductsBySellerId(@PathVariable String sellerId) {
    try {
      List<Product> products = productRepository.findBySellerId(sellerId);
      
      // Transform to visual-friendly format
      List<Map<String, Object>> visualProducts = products.stream()
          .map(this::toVisualProductMap)
          .collect(Collectors.toList());

      Map<String, Object> response = new HashMap<>();
      response.put("items", visualProducts);
      response.put("totalItems", products.size());
      response.put("lowStockCount", (int) products.stream().filter(Product::isLowStock).count());
      response.put("outOfStockCount", (int) products.stream().filter(Product::isOutOfStock).count());

      return ResponseEntity.ok(response);
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * Get seller dashboard summary.
   */
  @GetMapping("/seller/{sellerId}/dashboard")
  @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
  public ResponseEntity<?> getSellerDashboard(@PathVariable String sellerId) {
    try {
      List<Product> products = productRepository.findBySellerId(sellerId);
      
      Map<String, Object> dashboard = new HashMap<>();
      dashboard.put("totalProducts", products.size());
      dashboard.put("inStock", (int) products.stream().filter(p -> p.getStockQuantity() > 0).count());
      dashboard.put("lowStock", (int) products.stream().filter(Product::isLowStock).count());
      dashboard.put("outOfStock", (int) products.stream().filter(Product::isOutOfStock).count());
      
      // Category breakdown
      Map<String, Long> categoryBreakdown = products.stream()
          .collect(Collectors.groupingBy(
              Product::getCategory,
              Collectors.counting()
          ));
      dashboard.put("categoryBreakdown", categoryBreakdown);

      // Low stock items preview (top 5)
      List<Map<String, Object>> lowStockPreview = products.stream()
          .filter(Product::isLowStock)
          .limit(5)
          .map(this::toVisualProductMap)
          .collect(Collectors.toList());
      dashboard.put("lowStockPreview", lowStockPreview);

      return ResponseEntity.ok(dashboard);
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * Update stock quantity for a product.
   */
  @PutMapping("/seller/{sellerId}/product/{productId}/stock")
  @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
  public ResponseEntity<?> updateStock(@PathVariable String sellerId,
                                        @PathVariable String productId,
                                        @RequestBody Map<String, Integer> stockRequest) {
    try {
      Product product = productRepository.findBySellerIdAndId(sellerId, productId)
          .orElseThrow(() -> new RuntimeException("Product not found"));
      
      product.setStockQuantity(stockRequest.get("quantity"));
      productRepository.save(product);
      
      return ResponseEntity.ok(toVisualProductMap(product));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * Restock a product (add to existing stock).
   */
  @PostMapping("/seller/{sellerId}/product/{productId}/restock")
  @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
  public ResponseEntity<?> restockProduct(@PathVariable String sellerId,
                                           @PathVariable String productId,
                                           @RequestBody Map<String, Integer> restockRequest) {
    try {
      Product product = productRepository.findBySellerIdAndId(sellerId, productId)
          .orElseThrow(() -> new RuntimeException("Product not found"));
      
      Integer quantity = restockRequest.get("quantity");
      if (quantity == null || quantity <= 0) {
        return ResponseEntity.badRequest().body(Map.of("error", "Valid quantity is required"));
      }
      
      product.setStockQuantity(product.getStockQuantity() + quantity);
      productRepository.save(product);
      
      return ResponseEntity.ok(toVisualProductMap(product));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * Get low stock items for seller.
   */
  @GetMapping("/seller/{sellerId}/low-stock")
  @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
  public ResponseEntity<?> getLowStockItems(@PathVariable String sellerId) {
    try {
      List<Product> products = productRepository.findBySellerId(sellerId).stream()
          .filter(Product::isLowStock)
          .collect(Collectors.toList());
      
      List<Map<String, Object>> visualItems = products.stream()
          .map(this::toVisualProductMap)
          .collect(Collectors.toList());

      return ResponseEntity.ok(Map.of("items", visualItems, "alertType", "LOW_STOCK"));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * Get out of stock items for seller.
   */
  @GetMapping("/seller/{sellerId}/out-of-stock")
  @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
  public ResponseEntity<?> getOutOfStockItems(@PathVariable String sellerId) {
    try {
      List<Product> products = productRepository.findBySellerId(sellerId).stream()
          .filter(Product::isOutOfStock)
          .collect(Collectors.toList());
      
      List<Map<String, Object>> visualItems = products.stream()
          .map(this::toVisualProductMap)
          .collect(Collectors.toList());

      return ResponseEntity.ok(Map.of("items", visualItems, "alertType", "OUT_OF_STOCK"));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * Get a specific product by seller ID and product ID.
   */
  @GetMapping("/seller/{sellerId}/product/{productId}")
  @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
  public ResponseEntity<?> getSellerProduct(@PathVariable String sellerId, 
                                             @PathVariable String productId) {
    try {
      return productRepository.findBySellerIdAndId(sellerId, productId)
          .map(product -> ResponseEntity.ok(toVisualProductMap(product)))
          .orElse(ResponseEntity.notFound().build());
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * Get products by seller and category.
   */
  @GetMapping("/seller/{sellerId}/category/{category}")
  @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
  public ResponseEntity<?> getProductsBySellerAndCategory(@PathVariable String sellerId, 
                                                           @PathVariable String category) {
    try {
      List<Product> products = productRepository.findBySellerIdAndCategory(sellerId, category);
      
      List<Map<String, Object>> visualItems = products.stream()
          .map(this::toVisualProductMap)
          .collect(Collectors.toList());

      return ResponseEntity.ok(Map.of("items", visualItems, "category", category));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * Convert Product to visual-friendly map format.
   */
  private Map<String, Object> toVisualProductMap(Product product) {
    Map<String, Object> visual = new HashMap<>();
    visual.put("id", product.getId());
    visual.put("productId", product.getId());
    visual.put("name", product.getName());
    visual.put("imageUrl", product.getImageUrl());
    visual.put("category", product.getCategory());
    visual.put("price", product.getPrice());
    visual.put("stockQuantity", product.getStockQuantity() != null ? product.getStockQuantity() : 0);
    visual.put("lowStockThreshold", product.getLowStockThreshold() != null ? product.getLowStockThreshold() : 5);
    visual.put("isLowStock", product.isLowStock());
    visual.put("isOutOfStock", product.isOutOfStock());
    visual.put("co2EmissionScore", product.getCo2EmissionScore());
    visual.put("shippingMethod", product.getShippingMethod());
    visual.put("isHandmade", product.isHandmade());
    visual.put("status", product.getStatus());
    return visual;
  }
}
