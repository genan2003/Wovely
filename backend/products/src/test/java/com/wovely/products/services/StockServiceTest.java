package com.wovely.products.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wovely.common.error.ApiException;
import com.wovely.products.clients.InventoryClient;
import com.wovely.products.clients.InventoryClient.StockLevel;
import com.wovely.products.models.Product;
import com.wovely.products.repository.ProductRepository;

/** Seller stock edits are forwarded to inventory (ADR 0001) and applied to the read model at once. */
@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock InventoryClient inventory;
    @Mock StockReadModel readModel;
    @Mock ProductRepository products;
    @InjectMocks StockService service;

    Product p;
    Product refreshed;

    @BeforeEach
    void data() {
        p = new Product();
        p.setId("p-1");
        p.setSellerId("seller-1");
        p.setStatus("APPROVED");
        refreshed = new Product();
        refreshed.setId("p-1");
        refreshed.setStockQuantity(9);
        lenient().when(products.findBySellerIdAndId("seller-1", "p-1")).thenReturn(Optional.of(p));
        lenient().when(products.findById("p-1")).thenReturn(Optional.of(refreshed));
    }

    static StockLevel level(int qty, long version) {
        return new StockLevel("p-1", "seller-1", qty, 5, true, version);
    }

    @Test
    void restockAppliesInventorysAnswerToTheReadModel() {
        when(inventory.restock("p-1", "seller-1", 4)).thenReturn(level(9, 3));
        assertThat(service.restock("seller-1", "p-1", 4)).isSameAs(refreshed);
        verify(readModel).apply("p-1", 9, 5, 3L);
    }

    @Test
    void restockNeedsAPositiveQuantity() {
        for (Integer q : new Integer[] { null, 0, -2 }) {
            assertThatThrownBy(() -> service.restock("seller-1", "p-1", q))
                .isInstanceOf(ApiException.class).extracting("status").hasToString("400 BAD_REQUEST");
        }
        verify(inventory, never()).restock(anyString(), anyString(), anyInt());
    }

    @Test
    void setLevelAcceptsZeroButNotNegativeOrMissing() {
        when(inventory.setLevel("p-1", "seller-1", 0)).thenReturn(level(0, 4));
        service.setLevel("seller-1", "p-1", 0);
        verify(readModel).apply("p-1", 0, 5, 4L);
        assertThatThrownBy(() -> service.setLevel("seller-1", "p-1", -1)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.setLevel("seller-1", "p-1", null)).isInstanceOf(ApiException.class);
    }

    @Test
    void anotherSellersProductIs404() {
        when(products.findBySellerIdAndId("seller-2", "p-1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.setLevel("seller-2", "p-1", 3))
            .isInstanceOf(ApiException.class).extracting("status").hasToString("404 NOT_FOUND");
        verify(inventory, never()).setLevel(anyString(), anyString(), anyInt());
    }

    @Test
    void registerSeedsTheReadModel() {
        when(inventory.register(p, 7)).thenReturn(level(7, 1));
        service.register(p, 7);
        verify(readModel).apply("p-1", 7, 5, 1L);
    }

    @Test
    void answerWithoutVersionLeavesTheReadModelAlone() {
        when(inventory.register(p, 7)).thenReturn(new StockLevel("p-1", "seller-1", 7, 5, true, null));
        assertThat(service.register(p, 7)).isSameAs(p);
        verify(readModel, never()).apply(anyString(), anyInt(), any(), anyLong());
    }

    @Test
    void savingAnArchivedListingDeactivatesItsStock() {
        p.setStatus(Product.ARCHIVED);
        service.afterListingSaved(p);
        verify(inventory).deactivate("p-1", "seller-1");
        verify(inventory, never()).register(any(), anyInt());
    }

    @Test
    void savingAnActiveListingRefreshesInventorysCopy() {
        when(inventory.register(p, 0)).thenReturn(level(9, 6));
        service.afterListingSaved(p);
        verify(readModel).apply("p-1", 9, 5, 6L);
    }

    @Test
    void aFailedRefreshIsBestEffort() {
        when(inventory.register(p, 0)).thenThrow(ApiException.conflict("inventory unavailable"));
        service.afterListingSaved(p); // no exception: the next stock event repairs it
        verify(readModel, never()).apply(anyString(), anyInt(), any(), anyLong());
    }
}
