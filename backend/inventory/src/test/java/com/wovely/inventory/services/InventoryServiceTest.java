package com.wovely.inventory.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wovely.inventory.models.Inventory;
import com.wovely.inventory.models.InventoryItem;
import com.wovely.inventory.repository.InventoryItemRepository;
import com.wovely.inventory.repository.InventoryRepository;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    static final String SELLER = "seller-1";

    @Mock InventoryItemRepository items;
    @Mock InventoryRepository inventories;
    @InjectMocks InventoryService service;

    InventoryItem plenty, low, out, legacy;

    @BeforeEach
    void data() {
        plenty = item("p-plenty", 40, 5, "Mugs");
        low = item("p-low", 8, 10, "mugs");   // below its own threshold of 10 (would pass a fixed 5)
        out = item("p-out", 0, 5, "Scarves");
        legacy = item("p-legacy", null, null, null);
    }

    static InventoryItem item(String productId, Integer qty, Integer threshold, String category) {
        InventoryItem i = new InventoryItem();
        i.setProductId(productId);
        i.setSellerId(SELLER);
        i.setStockQuantity(qty);
        i.setLowStockThreshold(threshold);
        i.setCategory(category);
        return i;
    }

    void active(InventoryItem... list) {
        when(items.findBySellerIdAndIsActive(SELLER, true)).thenReturn(List.of(list));
    }

    @Test
    void lowStockUsesEachItemsOwnThresholdAndIncludesOutOfStock() {
        active(plenty, low, out);
        assertThat(service.getLowStockItems(SELLER)).extracting(InventoryItem::getProductId)
            .containsExactly("p-low", "p-out");
    }

    @Test
    void viewsOnlyReadActiveRecords() {
        // archived listings are deactivated; the repository is only ever asked for active ones
        active(plenty);
        service.getOutOfStockItems(SELLER);
        service.getInventoryItemsByCategory(SELLER, "Mugs");
        service.dashboard(SELLER);
        verify(items, never()).findBySellerId(any());
    }

    @Test
    void outOfStockIsZeroOrMissingQuantity() {
        active(plenty, out, legacy);
        assertThat(service.getOutOfStockItems(SELLER)).extracting(InventoryItem::getProductId)
            .containsExactly("p-out", "p-legacy");
    }

    @Test
    void categoryMatchIsCaseInsensitiveAndBlankIsUncategorised() {
        active(plenty, low, out, legacy);
        assertThat(service.getInventoryItemsByCategory(SELLER, "MUGS")).hasSize(2);
        assertThat(service.getInventoryItemsByCategory(SELLER, InventoryService.UNCATEGORISED))
            .extracting(InventoryItem::getProductId).containsExactly("p-legacy");
    }

    @Test
    @SuppressWarnings("unchecked")
    void dashboardCountsAndPreviewsTheLowestFirst() {
        active(plenty, low, out, legacy);
        Map<String, Object> d = service.dashboard(SELLER);
        assertThat(d).containsEntry("totalProducts", 4).containsEntry("inStock", 2L)
            .containsEntry("lowStock", 3L).containsEntry("outOfStock", 2L);
        // legacy record without a category must not break the breakdown (groupingBy threw on null)
        assertThat((Map<String, Long>) d.get("categoryBreakdown"))
            .containsEntry("Mugs", 1L).containsEntry("mugs", 1L).containsEntry("Scarves", 1L)
            .containsEntry(InventoryService.UNCATEGORISED, 1L);
        List<Map<String, Object>> preview = (List<Map<String, Object>>) d.get("lowStockPreview");
        assertThat(preview).extracting(m -> m.get("stockQuantity")).containsExactly(0, 0, 8);
    }

    @Test
    void previewIsCappedAtFive() {
        InventoryItem[] many = new InventoryItem[8];
        for (int i = 0; i < many.length; i++) many[i] = item("p" + i, i, 10, "Mugs");
        active(many);
        assertThat((List<?>) service.dashboard(SELLER).get("lowStockPreview")).hasSize(InventoryService.PREVIEW_SIZE);
    }

    @Test
    void summaryCountsActiveItems() {
        active(plenty, low, out);
        Map<String, Object> s = service.summary(SELLER);
        assertThat(s).containsEntry("totalItems", 3).containsEntry("lowStockCount", 2L).containsEntry("outOfStockCount", 1L);
        assertThat((List<?>) s.get("items")).hasSize(3);
    }

    @Test
    void viewFillsDefaultsForLegacyRecords() {
        Map<String, Object> v = InventoryService.toView(legacy);
        assertThat(v).containsEntry("stockQuantity", 0).containsEntry("lowStockThreshold", 5)
            .containsEntry("isLowStock", true).containsEntry("isOutOfStock", true)
            .containsEntry("productId", "p-legacy");
    }

    @Test
    void statsAreRefreshedFromActiveItems() {
        Inventory inv = new Inventory(SELLER, "Seller");
        when(inventories.findBySellerId(SELLER)).thenReturn(Optional.of(inv));
        active(plenty, low, out);
        service.updateInventoryStats(SELLER);
        ArgumentCaptor<Inventory> saved = ArgumentCaptor.forClass(Inventory.class);
        verify(inventories).save(saved.capture());
        assertThat(saved.getValue().getTotalItems()).isEqualTo(3);
        assertThat(saved.getValue().getLowStockItems()).isEqualTo(2);
        assertThat(saved.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void statsAreSkippedWithoutAnInventoryDocument() {
        when(inventories.findBySellerId(SELLER)).thenReturn(Optional.empty());
        service.updateInventoryStats(SELLER);
        verify(inventories, never()).save(any());
    }

    @Test
    void singleItemLookupDelegates() {
        when(items.findBySellerIdAndProductId(SELLER, "p-low")).thenReturn(Optional.of(low));
        assertThat(service.getInventoryItem(SELLER, "p-low")).contains(low);
    }
}
