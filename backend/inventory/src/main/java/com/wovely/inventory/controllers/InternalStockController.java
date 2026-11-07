package com.wovely.inventory.controllers;

import java.util.LinkedHashMap;
import java.util.Map;

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
import com.wovely.inventory.models.InventoryItem;
import com.wovely.inventory.stock.StockLedger;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Stock API for the other services (internal API key only, PLAN.md 4.2). Inventory is the single
 * writer; the seller id must match the stored one, so a service can't move someone else's stock.
 */
@RestController
@RequestMapping("/internal/inventory/stock/{productId}")
public class InternalStockController {

    public record InitRequest(@NotBlank String sellerId, @NotNull @Min(0) Integer initialQuantity,
            @Min(0) Integer lowStockThreshold, String productName, @Min(0) Long priceCents, String imageUrl, String category,
            String co2EmissionScore, String shippingMethod, Boolean isHandmade) {
    }

    public record QuantityRequest(@NotBlank String sellerId, @NotNull Integer quantity) {
    }

    private final StockLedger ledger;

    public InternalStockController(StockLedger ledger) {
        this.ledger = ledger;
    }

    @PutMapping
    public Map<String, Object> init(@PathVariable String productId, @Valid @RequestBody InitRequest r) {
        return view(ledger.init(productId, r.sellerId(), r.initialQuantity(), r.lowStockThreshold(),
                new StockLedger.Details(r.productName(), r.priceCents(), r.imageUrl(), r.category(), r.co2EmissionScore(),
                        r.shippingMethod(), r.isHandmade())));
    }

    @GetMapping
    public Map<String, Object> get(@PathVariable String productId, @RequestParam String sellerId) {
        return view(ledger.find(productId, sellerId).orElseThrow(() -> ApiException.notFound("No stock record")));
    }

    @PostMapping("/reserve")
    public Map<String, Object> reserve(@PathVariable String productId, @Valid @RequestBody QuantityRequest r) {
        return view(ledger.reserve(productId, r.sellerId(), r.quantity()));
    }

    @PostMapping("/release")
    public Map<String, Object> release(@PathVariable String productId, @Valid @RequestBody QuantityRequest r) {
        return view(ledger.release(productId, r.sellerId(), r.quantity()));
    }

    @PostMapping("/restock")
    public Map<String, Object> restock(@PathVariable String productId, @Valid @RequestBody QuantityRequest r) {
        return view(ledger.restock(productId, r.sellerId(), r.quantity()));
    }

    @PutMapping("/level")
    public Map<String, Object> setLevel(@PathVariable String productId, @Valid @RequestBody QuantityRequest r) {
        return view(ledger.setLevel(productId, r.sellerId(), r.quantity()));
    }

    @DeleteMapping
    public Map<String, Object> deactivate(@PathVariable String productId, @RequestParam String sellerId) {
        return view(ledger.deactivate(productId, sellerId));
    }

    static Map<String, Object> view(InventoryItem i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("productId", i.getProductId());
        m.put("sellerId", i.getSellerId());
        m.put("stockQuantity", i.getStockQuantity());
        m.put("lowStockThreshold", i.getLowStockThreshold());
        m.put("active", !Boolean.FALSE.equals(i.getIsActive()));
        m.put("version", i.getVersion());
        return m;
    }
}
