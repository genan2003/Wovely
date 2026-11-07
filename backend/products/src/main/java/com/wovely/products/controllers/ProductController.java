package com.wovely.products.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wovely.common.error.ApiException;
import com.wovely.common.security.OwnershipGuard;
import com.wovely.products.models.EDeliveryEmission;
import com.wovely.products.models.Product;
import com.wovely.products.payload.ListingRequest;
import com.wovely.products.repository.ProductRepository;
import com.wovely.products.services.CatalogQueryService;
import com.wovely.products.services.ListingService;
import com.wovely.products.services.SellerListingQueries;
import com.wovely.products.services.StockService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products")
public class ProductController {

  private final ProductRepository productRepository;
  private final CatalogQueryService catalogQuery;
  private final ListingService listings;
  private final SellerListingQueries sellerViews;
  private final StockService stockService;

  public ProductController(ProductRepository productRepository, CatalogQueryService catalogQuery,
      ListingService listings, SellerListingQueries sellerViews, StockService stockService) {
    this.productRepository = productRepository;
    this.catalogQuery = catalogQuery;
    this.listings = listings;
    this.sellerViews = sellerViews;
    this.stockService = stockService;
  }

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
      @RequestParam(required = false) EDeliveryEmission deliveryEmission,
      @RequestParam(required = false) Boolean localPickup,
      @RequestParam(required = false) Boolean isHandmade,
      @RequestParam(required = false) String city,
      @RequestParam(required = false) String region,
      @RequestParam(required = false) java.math.BigDecimal minPrice,
      @RequestParam(required = false) java.math.BigDecimal maxPrice,
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
  public Product getProductById(@PathVariable("id") String id) {
    return productRepository.findById(id).orElseThrow(() -> ApiException.notFound("Product not found"));
  }

  /**
   * Create a listing. The seller is the caller (admins may create on behalf of {@code sellerId});
   * new listings await admin approval (PENDING).
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
  public ResponseEntity<Product> createProduct(@Valid @RequestBody ListingRequest request) {
    return new ResponseEntity<>(listings.create(request, OwnershipGuard.current()), HttpStatus.CREATED);
  }

  // ------------------------------------------------------------------ seller views (own listings)

  /** All of a seller's listings that aren't archived, with counters. */
  @GetMapping("/seller/{sellerId}")
  @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
  public Map<String, Object> getProductsBySellerId(@PathVariable String sellerId) {
    return sellerViews.summary(sellerId);
  }

  @GetMapping("/seller/{sellerId}/dashboard")
  @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
  public Map<String, Object> getSellerDashboard(@PathVariable String sellerId) {
    return sellerViews.dashboard(sellerId);
  }

  @GetMapping("/seller/{sellerId}/low-stock")
  @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
  public Map<String, Object> getLowStockItems(@PathVariable String sellerId) {
    return Map.of("items", sellerViews.lowStock(sellerId), "alertType", "LOW_STOCK");
  }

  @GetMapping("/seller/{sellerId}/out-of-stock")
  @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
  public Map<String, Object> getOutOfStockItems(@PathVariable String sellerId) {
    return Map.of("items", sellerViews.outOfStock(sellerId), "alertType", "OUT_OF_STOCK");
  }

  @GetMapping("/seller/{sellerId}/category/{category}")
  @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
  public Map<String, Object> getProductsBySellerAndCategory(@PathVariable String sellerId, @PathVariable String category) {
    return Map.of("items", sellerViews.byCategory(sellerId, category), "category", category);
  }

  @GetMapping("/seller/{sellerId}/product/{productId}")
  @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
  public Map<String, Object> getSellerProduct(@PathVariable String sellerId, @PathVariable String productId) {
    return sellerViews.one(sellerId, productId);
  }

  // ------------------------------------------------------------------ seller writes

  /** Set the absolute stock level (forwarded to inventory, the owner). */
  @PutMapping("/seller/{sellerId}/product/{productId}/stock")
  @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
  public Map<String, Object> updateStock(@PathVariable String sellerId, @PathVariable String productId,
      @RequestBody(required = false) Map<String, Integer> body) {
    return SellerListingQueries.toView(stockService.setLevel(sellerId, productId, quantity(body)));
  }

  /** Add units to the stock level. */
  @PostMapping("/seller/{sellerId}/product/{productId}/restock")
  @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
  public Map<String, Object> restockProduct(@PathVariable String sellerId, @PathVariable String productId,
      @RequestBody(required = false) Map<String, Integer> body) {
    return SellerListingQueries.toView(stockService.restock(sellerId, productId, quantity(body)));
  }

  /**
   * Edit a listing. Missing fields keep their value. Content edits of an approved listing, and any
   * edit of a listing that needs changes, send it back for review ({@code status} in the response).
   */
  @PutMapping("/seller/{sellerId}/product/{productId}")
  @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
  public Map<String, Object> updateProduct(@PathVariable String sellerId, @PathVariable String productId,
      @Valid @RequestBody ListingRequest request) {
    return SellerListingQueries.toView(listings.updateBySeller(sellerId, productId, request));
  }

  /** Soft delete (PLAN.md 2.8). */
  @DeleteMapping("/seller/{sellerId}/product/{productId}")
  @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")
  public Map<String, String> deleteProduct(@PathVariable String sellerId, @PathVariable String productId) {
    listings.archive(sellerId, productId);
    return Map.of("message", "Product deleted successfully");
  }

  private static Integer quantity(Map<String, Integer> body) {
    return body == null ? null : body.get("quantity");
  }
}
