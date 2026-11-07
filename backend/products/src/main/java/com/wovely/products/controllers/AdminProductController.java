package com.wovely.products.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wovely.products.models.Product;
import com.wovely.products.payload.ListingRequest;
import com.wovely.products.repository.ProductRepository;
import com.wovely.products.services.ListingService;

import jakarta.validation.Valid;

/** Listing moderation (admin only). Empty lists are returned as {@code []} with 200. */
@RestController
@RequestMapping("/api/products/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

  private final ProductRepository productRepository;
  private final ListingService listings;

  public AdminProductController(ProductRepository productRepository, ListingService listings) {
    this.productRepository = productRepository;
    this.listings = listings;
  }

  /** The approval queue, including listings resubmitted after a seller edit. */
  @GetMapping("/pending")
  public List<Product> getPendingProducts() {
    return productRepository.findByStatus(ListingService.PENDING);
  }

  @GetMapping("/seller/{sellerId}")
  public List<Product> getProductsBySeller(@PathVariable("sellerId") String sellerId) {
    return productRepository.findBySellerId(sellerId);
  }

  @PutMapping("/{id}/status")
  public Product updateProductStatus(@PathVariable("id") String id, @RequestBody(required = false) Map<String, String> body) {
    Map<String, String> b = body == null ? Map.of() : body;
    return listings.moderate(id, b.get("status"), b.get("reason"));
  }

  @PutMapping("/{id}")
  public Product editProductContent(@PathVariable("id") String id, @Valid @RequestBody ListingRequest request) {
    return listings.editByAdmin(id, request);
  }
}
