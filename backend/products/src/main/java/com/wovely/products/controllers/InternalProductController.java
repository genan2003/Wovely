package com.wovely.products.controllers;

import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wovely.products.models.Product;
import com.wovely.products.services.StockService;

/**
 * Stock mutations used by the core service during checkout/cancellation.
 * Internal API key only — see PLAN.md 1.3. Errors: 400 bad quantity, 404 unknown product, 409 short stock.
 */
@RestController
@RequestMapping("/internal/products/seller/{sellerId}/product/{productId}")
public class InternalProductController {

    private final StockService stockService;

    public InternalProductController(StockService stockService) {
        this.stockService = stockService;
    }

    @PostMapping("/reduce-stock")
    public Map<String, Object> reduceStock(@PathVariable String sellerId, @PathVariable String productId,
            @RequestBody Map<String, Integer> body) {
        return stock(stockService.reduce(sellerId, productId, body.get("quantity")));
    }

    @PostMapping("/restock")
    public Map<String, Object> restock(@PathVariable String sellerId, @PathVariable String productId,
            @RequestBody Map<String, Integer> body) {
        return stock(stockService.restock(sellerId, productId, body.get("quantity")));
    }

    private static Map<String, Object> stock(Product p) {
        return Map.of("productId", p.getId(), "stockQuantity", p.getStockQuantity());
    }
}
