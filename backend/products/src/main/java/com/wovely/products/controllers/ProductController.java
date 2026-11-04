package com.wovely.products.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.wovely.products.models.Product;
import com.wovely.products.repository.ProductRepository;
import com.wovely.common.error.ApiException;
import com.wovely.common.security.JwtPrincipal;
import com.wovely.common.security.OwnershipGuard;
import com.wovely.products.services.StockService;
import com.wovely.products.services.CatalogQueryService;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
public class ProductController {

  @Autowired
  ProductRepository productRepository;

  @Autowired
  StockService stockService;

  @Autowired
  CatalogQueryService catalogQuery;

  /**
   * Public catalog: APPROVED products only, filtered in MongoDB (PLAN.md 2.10).
   * Returns a JSON array (unchanged contract). With {@code page} (zero-based) and optional {@code size}
   * (max 100) the result is paginated and {@code X-Total-Count} carries the number of matches.
   */
  @GetMapping
  public ResponseEntity<List<Product>> getAllProducts(
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String categoryPath,
      @RequestParam(required = false) Boolean isEco,
      @RequestParam(required = false) com.wovely.products.models.EDeliveryEmission deliveryEmission,
      @RequestParam(required = false) Boolean localPickup,
      @RequestParam(required = false) Boolean isHandmade,
      @RequestParam(required = false) String city,
      @RequestParam(required = false) String region,
      @RequestParam(required = false) Double minPrice,
      @RequestParam(required = false) Double maxPrice,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    var result = catalogQuery.search(
        new CatalogQueryService.Filter(category, categoryPath, isEco, deliveryEmission, localPickup, isHandmade, city,
            region, minPrice, maxPrice),
        page, size);
    return ResponseEntity.ok()
        .header("X-Total-Count", String.valueOf(result.total()))
        .body(result.items());
  }

  @GetMapping("/{id}")
  public ResponseEntity<Product> getProductById(@PathVariable("id") String id) {
    return productRepository.findById(id)
        .map(product -> new ResponseEntity<>(product, HttpStatus.OK))
        .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
  }

  /**
   * Create a listing. The seller is the caller (admins may create on behalf of {@code sellerId});
   * client-supplied ids are ignored and new listings await admin approval (PENDING).
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
  public ResponseEntity<Product> createProduct(@RequestBody Product product) {
    JwtPrincipal caller = OwnershipGuard.current();
    String sellerId = caller.isAdmin() && product.getSellerId() != null ? product.getSellerId() : caller.userId();
    if (sellerId == null) {
      throw ApiException.forbidden("Token has no user id; please sign in again");
    }
    if (product.getName() == null || product.getName().isBlank() || product.getPrice() == null || product.getPrice() < 0) {
      throw ApiException.badRequest("name and a non-negative price are required");
    }
    Product _product = new Product(
        product.getName(),
        product.getDescription(),
        product.getPrice(),
        sellerId,
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
        product.getStockQuantity() != null ? Math.max(0, product.getStockQuantity()) : 0,
        product.getLowStockThreshold() != null ? product.getLowStockThreshold() : 5);
    _product.setId(null);
    _product.setStatus("PENDING");
    _product.setLocalPickup(Boolean.TRUE.equals(product.getLocalPickup()));
    _product.refreshDeliveryEmission();
    int initialStock = _product.getStockQuantity() != null ? _product.getStockQuantity() : 0;
    Product savedProduct = productRepository.save(_product);
    try {
      // Inventory owns stock (PLAN.md 4.2): a listing without a stock record can't be sold, so undo on failure
      savedProduct = stockService.register(savedProduct, initialStock);
    } catch (RuntimeException e) {
      productRepository.delete(savedProduct);
      throw e;
    }
    return new ResponseEntity<>(savedProduct, HttpStatus.CREATED);
  }

  /**
   * Get all products by seller ID (including non-approved ones for inventory management).
   */
  @GetMapping("/seller/{sellerId}")
  @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
  public ResponseEntity<?> getProductsBySellerId(@PathVariable String sellerId) {
    try {
      List<Product> products = productRepository.findBySellerIdAndStatusNot(sellerId, Product.ARCHIVED);
      
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
  @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
  public ResponseEntity<?> getSellerDashboard(@PathVariable String sellerId) {
    try {
      List<Product> products = productRepository.findBySellerIdAndStatusNot(sellerId, Product.ARCHIVED);
      
      Map<String, Object> dashboard = new HashMap<>();
      dashboard.put("totalProducts", products.size());
      dashboard.put("inStock", (int) products.stream().filter(p -> p.getStockQuantity() != null && p.getStockQuantity() > 0).count());
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
  @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
  public ResponseEntity<?> updateStock(@PathVariable String sellerId,
                                        @PathVariable String productId,
                                        @RequestBody Map<String, Integer> stockRequest) {
    Integer quantity = stockRequest == null ? null : stockRequest.get("quantity");
    return ResponseEntity.ok(toVisualProductMap(stockService.setLevel(sellerId, productId, quantity)));
  }

  /**
   * Restock a product (add to existing stock).
   */
  @PostMapping("/seller/{sellerId}/product/{productId}/restock")
  @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
  public ResponseEntity<?> restockProduct(@PathVariable String sellerId,
                                           @PathVariable String productId,
                                           @RequestBody Map<String, Integer> restockRequest) {
    Product product = stockService.restock(sellerId, productId, restockRequest.get("quantity"));
    return ResponseEntity.ok(toVisualProductMap(product));
  }

  /**
   * Update full product details for a seller.
   */
  @PutMapping("/seller/{sellerId}/product/{productId}")
  @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
  public ResponseEntity<?> updateProduct(@PathVariable String sellerId,
                                        @PathVariable String productId,
                                        @RequestBody Product product) {
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
      if (product.getLocalPickup() != null) {
        _product.setLocalPickup(product.getLocalPickup());
      }
      _product.refreshDeliveryEmission();
      if (product.getLowStockThreshold() != null) {
        _product.setLowStockThreshold(product.getLowStockThreshold());
      }
      
      Product saved = productRepository.save(_product);
      stockService.afterListingSaved(saved);
      if (product.getStockQuantity() != null) {
        // stock edits go to inventory, the owner (PLAN.md 4.2)
        saved = stockService.setLevel(sellerId, productId, product.getStockQuantity());
      } else {
        saved = productRepository.findById(productId).orElse(saved);
      }
      return new ResponseEntity<>(toVisualProductMap(saved), HttpStatus.OK);
    } else {
      return new ResponseEntity<>("Product not found with id: " + productId, HttpStatus.NOT_FOUND);
    }
  }

  /**
   * Delete a product for a seller.
   */
  @DeleteMapping("/seller/{sellerId}/product/{productId}")
  @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
  public ResponseEntity<?> deleteProduct(@PathVariable String sellerId,
                                        @PathVariable String productId) {
    Product product = productRepository.findBySellerIdAndId(sellerId, productId)
        .orElseThrow(() -> com.wovely.common.error.ApiException.notFound("Product not found"));
    
    // Soft delete (PLAN.md 2.8): order history keeps resolving the product, it leaves the catalog,
    // seller views and the inventory mirror, and checkout rejects it (status != APPROVED).
    product.setStatus(Product.ARCHIVED);
    productRepository.save(product);
    stockService.afterListingSaved(product); // deactivates the stock record
    return ResponseEntity.ok(Map.of("message", "Product deleted successfully"));
  }

  /**
   * Get low stock items for seller.
   */
  @GetMapping("/seller/{sellerId}/low-stock")
  @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
  public ResponseEntity<?> getLowStockItems(@PathVariable String sellerId) {
    try {
      List<Product> products = productRepository.findBySellerIdAndStatusNot(sellerId, Product.ARCHIVED).stream()
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
  @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
  public ResponseEntity<?> getOutOfStockItems(@PathVariable String sellerId) {
    try {
      List<Product> products = productRepository.findBySellerIdAndStatusNot(sellerId, Product.ARCHIVED).stream()
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
  @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
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
  @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
  public ResponseEntity<?> getProductsBySellerAndCategory(@PathVariable String sellerId, 
                                                           @PathVariable String category) {
    try {
      List<Product> products = productRepository.findBySellerIdAndCategoryAndStatusNot(sellerId, category, Product.ARCHIVED);
      
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
    visual.put("localPickup", Boolean.TRUE.equals(product.getLocalPickup()));
    visual.put("deliveryEmission", product.getDeliveryEmission());
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
