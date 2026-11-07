package com.wovely.products.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wovely.common.error.ApiException;
import com.wovely.products.models.Product;
import com.wovely.products.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class SellerListingQueriesTest {

    static final String SELLER = "seller-1";

    @Mock ProductRepository products;
    @InjectMocks SellerListingQueries queries;

    static Product p(String id, Integer qty, String category, String status) {
        Product p = new Product();
        p.setId(id);
        p.setSellerId(SELLER);
        p.setStockQuantity(qty);
        p.setLowStockThreshold(5);
        p.setCategory(category);
        p.setStatus(status);
        return p;
    }

    @BeforeEach
    void data() {
        lenient().when(products.findBySellerIdAndStatusNot(SELLER, Product.ARCHIVED)).thenReturn(List.of(
            p("a", 30, "Ceramics", "APPROVED"),
            p("b", 2, "Ceramics", "PENDING"),
            p("c", 0, null, "NEEDS_CHANGES"),
            p("d", null, " ", "APPROVED")));
    }

    @Test
    void summaryCountsStockAndReviewStates() {
        Map<String, Object> s = queries.summary(SELLER);
        assertThat(s).containsEntry("totalItems", 4).containsEntry("lowStockCount", 2L)
            .containsEntry("outOfStockCount", 2L).containsEntry("pendingReviewCount", 1L)
            .containsEntry("needsChangesCount", 1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dashboardToleratesMissingCategoriesAndSortsThePreview() {
        Map<String, Object> d = queries.dashboard(SELLER);
        assertThat(d).containsEntry("totalProducts", 4).containsEntry("inStock", 2L);
        assertThat((Map<String, Long>) d.get("categoryBreakdown"))
            .containsEntry("Ceramics", 2L).containsEntry(SellerListingQueries.UNCATEGORISED, 2L);
        assertThat((List<Map<String, Object>>) d.get("lowStockPreview")).extracting(m -> m.get("productId"))
            .containsExactly("c", "b");
    }

    @Test
    void alertsFilterTheActiveListings() {
        assertThat(queries.lowStock(SELLER)).extracting(m -> m.get("productId")).containsExactly("b", "c");
        assertThat(queries.outOfStock(SELLER)).extracting(m -> m.get("productId")).containsExactly("c", "d");
    }

    @Test
    void byCategoryExcludesArchived() {
        when(products.findBySellerIdAndCategoryAndStatusNot(SELLER, "Ceramics", Product.ARCHIVED))
            .thenReturn(List.of(p("a", 30, "Ceramics", "APPROVED")));
        assertThat(queries.byCategory(SELLER, "Ceramics")).hasSize(1);
    }

    @Test
    void oneReturnsTheViewWithItsStatusAndHidesArchived() {
        when(products.findBySellerIdAndId(SELLER, "b")).thenReturn(Optional.of(p("b", 2, "Ceramics", "PENDING")));
        assertThat(queries.one(SELLER, "b")).containsEntry("status", "PENDING").containsEntry("isLowStock", true);
        when(products.findBySellerIdAndId(SELLER, "x")).thenReturn(Optional.of(p("x", 2, "Ceramics", Product.ARCHIVED)));
        assertThatThrownBy(() -> queries.one(SELLER, "x")).isInstanceOf(ApiException.class);
    }

    @Test
    void viewDefaultsMissingStockFields() {
        Product legacy = new Product();
        legacy.setId("z");
        assertThat(SellerListingQueries.toView(legacy)).containsEntry("stockQuantity", 0)
            .containsEntry("lowStockThreshold", Product.DEFAULT_LOW_STOCK_THRESHOLD).containsEntry("localPickup", false);
    }
}
