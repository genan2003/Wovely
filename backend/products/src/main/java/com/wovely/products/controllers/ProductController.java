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

  private final org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
  private final String INVENTORY_API = "http://localhost:8083/api/inventory";

  /**
   * Sync changes with the inventory service.
   */
  private void syncWithInventory(String sellerId, String productId, Product product) {
    try {
      String url = INVENTORY_API + "/seller/" + sellerId + "/product/" + productId;
      
      Map<String, Object> details = new HashMap<>();
      details.put("productName", product.getName());
      details.put("price", product.getPrice());
      details.put("imageUrl", product.getImageUrl());
      details.put("category", product.getCategory());
      details.put("lowStockThreshold", product.getLowStockThreshold());
      details.put("co2EmissionScore", product.getCo2EmissionScore());
      details.put("shippingMethod", product.getShippingMethod());
      details.put("isHandmade", product.getHandmade());
      details.put("stockQuantity", product.getStockQuantity());
      
      restTemplate.put(url, details);
    } catch (Exception e) {
      System.err.println("Failed to sync with inventory service: " + e.getMessage());
    }
  }

  @GetMapping
  public ResponseEntity<List<Product>> getAllProducts(
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String categoryPath,
      @RequestParam(required = false) Boolean isEco,
      @RequestParam(required = false) Boolean isHandmade,
      @RequestParam(required = false) String city,
      @RequestParam(required = false) String region,
      @RequestParam(required = false) Double minPrice,
      @RequestParam(required = false) Double maxPrice) {
    try {
      List<Product> products = productRepository.findByStatus("APPROVED");

      if (category != null && !category.isEmpty()) {
        products = products.stream()
            .filter(p -> category.equals(p.getCategory()))
            .collect(Collectors.toList());
      }

      if (categoryPath != null && !categoryPath.isEmpty()) {
        products = products.stream()
            .filter(p -> p.getCategoryPath() != null && p.getCategoryPath().startsWith(categoryPath))
            .collect(Collectors.toList());
      }

      if (isEco != null && isEco) {
        products = products.stream()
            .filter(p -> "Low".equalsIgnoreCase(p.getCo2EmissionScore()))
            .collect(Collectors.toList());
      }

      if (isHandmade != null && isHandmade) {
        products = products.stream()
            .filter(p -> p.getHandmade() != null && p.getHandmade())
            .collect(Collectors.toList());
      }

      if (city != null && !city.isEmpty()) {
        String searchCity = city.trim().toLowerCase();
        products = products.stream()
            .filter(p -> p.getCity() != null && p.getCity().toLowerCase().contains(searchCity))
            .collect(Collectors.toList());
      }

      if (region != null && !region.isEmpty()) {
        String searchRegion = region.trim().toLowerCase();
        products = products.stream()
            .filter(p -> p.getRegion() != null && p.getRegion().toLowerCase().contains(searchRegion))
            .collect(Collectors.toList());
      }

      if (minPrice != null) {
        products = products.stream()
            .filter(p -> p.getPrice() != null && p.getPrice() >= minPrice)
            .collect(Collectors.toList());
      }

      if (maxPrice != null) {
        products = products.stream()
            .filter(p -> p.getPrice() != null && p.getPrice() <= maxPrice)
            .collect(Collectors.toList());
      }

      return new ResponseEntity<>(products, HttpStatus.OK);
    } catch (Exception e) {
      System.err.println("Search error: " + e.getMessage());
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
      Product _product = new Product(
          product.getName(),
          product.getDescription(),
          product.getPrice(),
          product.getSellerId(),
          product.getImageUrl(),
          product.getCategory(),
          product.getCategoryPath(),
          product.getMaterials(),
          product.getCity(),
          product.getRegion(),
          product.getLatitude(),
          product.getLongitude(),
          product.getCo2EmissionScore(),
          product.getShippingMethod(),
          product.getHandmade(),
          product.getStockQuantity() != null ? product.getStockQuantity() : 0,
          product.getLowStockThreshold() != null ? product.getLowStockThreshold() : 5);
      
      if (product.getId() != null) {
          _product.setId(product.getId());
      }
      
      _product.setStatus("APPROVED");
      Product savedProduct = productRepository.save(_product);
      return new ResponseEntity<>(savedProduct, HttpStatus.CREATED);
    } catch (Exception e) {
      System.err.println("Error creating product: " + e.getMessage());
      e.printStackTrace();
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
      syncWithInventory(sellerId, productId, product);
      
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
      syncWithInventory(sellerId, productId, product);
      
      return ResponseEntity.ok(toVisualProductMap(product));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * Reduce stock for a product (Real-Time Deduction).
   * Returns error if not enough stock available.
   */
  @PostMapping("/seller/{sellerId}/product/{productId}/reduce-stock")
  public ResponseEntity<?> reduceStock(@PathVariable String sellerId,
                                        @PathVariable String productId,
                                        @RequestBody Map<String, Integer> reduceRequest) {
    try {
      Product product = productRepository.findBySellerIdAndId(sellerId, productId)
          .orElseThrow(() -> new RuntimeException("Product not found"));
      
      Integer quantity = reduceRequest.get("quantity");
      if (quantity == null || quantity <= 0) {
        return ResponseEntity.badRequest().body(Map.of("error", "Valid quantity is required"));
      }

      if (product.getStockQuantity() < quantity) {
        return ResponseEntity.badRequest().body(Map.of(
            "error", "Not enough stock available",
            "available", product.getStockQuantity(),
            "requested", quantity
        ));
      }
      
      product.setStockQuantity(product.getStockQuantity() - quantity);
      productRepository.save(product);
      syncWithInventory(sellerId, productId, product);
      
      return ResponseEntity.ok(toVisualProductMap(product));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * Update full product details for a seller.
   */
  @PutMapping("/seller/{sellerId}/product/{productId}")
  @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
  public ResponseEntity<?> updateProduct(@PathVariable String sellerId,
                                        @PathVariable String productId,
                                        @RequestBody Product product) {
    try {
      java.util.Optional<Product> productData = productRepository.findBySellerIdAndId(sellerId, productId);
      
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
        _product.setLowStockThreshold(product.getLowStockThreshold());
        
        if (product.getStockQuantity() != null) {
          _product.setStockQuantity(product.getStockQuantity());
        }
        
        Product saved = productRepository.save(_product);
        syncWithInventory(sellerId, productId, saved);
        return new ResponseEntity<>(toVisualProductMap(saved), HttpStatus.OK);
      } else {
        return new ResponseEntity<>("Product not found with id: " + productId, HttpStatus.NOT_FOUND);
      }
    } catch (Exception e) {
      System.err.println("Update error: " + e.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * Delete a product for a seller.
   */
  @DeleteMapping("/seller/{sellerId}/product/{productId}")
  @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
  public ResponseEntity<?> deleteProduct(@PathVariable String sellerId,
                                        @PathVariable String productId) {
    try {
      Product product = productRepository.findBySellerIdAndId(sellerId, productId)
          .orElseThrow(() -> new RuntimeException("Product not found or unauthorized"));
      
      productRepository.delete(product);
      return ResponseEntity.ok(Map.of("message", "Product deleted successfully"));
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
    visual.put("productName", product.getName());
    visual.put("description", product.getDescription());
    visual.put("imageUrl", product.getImageUrl());
    visual.put("category", product.getCategory());
    visual.put("price", product.getPrice());
    visual.put("stockQuantity", product.getStockQuantity() != null ? product.getStockQuantity() : 0);
    visual.put("lowStockThreshold", product.getLowStockThreshold() != null ? product.getLowStockThreshold() : 5);
    visual.put("isLowStock", product.isLowStock());
    visual.put("isOutOfStock", product.isOutOfStock());
    visual.put("co2EmissionScore", product.getCo2EmissionScore());
    visual.put("shippingMethod", product.getShippingMethod());
    visual.put("isHandmade", product.getHandmade());
    visual.put("status", product.getStatus());
    visual.put("categoryPath", product.getCategoryPath());
    visual.put("materials", product.getMaterials());
    visual.put("city", product.getCity());
    visual.put("region", product.getRegion());
    visual.put("latitude", product.getLatitude());
    visual.put("longitude", product.getLongitude());
    return visual;
  }
}
