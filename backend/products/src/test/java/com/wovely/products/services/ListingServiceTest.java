package com.wovely.products.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wovely.common.error.ApiException;
import com.wovely.common.security.JwtPrincipal;
import com.wovely.products.clients.AuditClient;
import com.wovely.products.models.EDeliveryEmission;
import com.wovely.products.models.Product;
import com.wovely.products.payload.ListingRequest;
import com.wovely.products.repository.ProductRepository;

/** Review rules for seller edits (docs/diagrams/state/product-status.puml). */
@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    static final String SELLER = "seller-1";

    @Mock ProductRepository products;
    @Mock StockService stock;
    @Mock AuditClient audit;
    @InjectMocks ListingService service;

    Product listing;

    @BeforeEach
    void data() {
        listing = new Product();
        listing.setId("p-1");
        listing.setSellerId(SELLER);
        listing.setName("Blue mug");
        listing.setDescription("Wheel-thrown");
        listing.setPriceCents(8000L);
        listing.setImageUrl("/img/mug.jpg");
        listing.setCategory("Ceramics");
        listing.setMaterials(List.of("clay", "glaze"));
        listing.setLatitude(44.43);
        listing.setLongitude(26.10);
        listing.setLocalPickup(true);
        listing.setHandmade(true);
        listing.setStatus(ListingService.APPROVED);
        lenient().when(products.findBySellerIdAndId(SELLER, "p-1")).thenReturn(Optional.of(listing));
        lenient().when(products.findById("p-1")).thenReturn(Optional.of(listing));
        lenient().when(products.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    static ListingRequest req() {
        return new ListingRequest(null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null);
    }

    static ListingRequest named(String name) {
        return new ListingRequest(name, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null);
    }

    static ListingRequest namedAndPriced(String name, double price) {
        return new ListingRequest(name, null, java.math.BigDecimal.valueOf(price), null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null);
    }

    static ListingRequest priced(double price) {
        return new ListingRequest(null, null, java.math.BigDecimal.valueOf(price), null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null);
    }

    // ------------------------------------------------------------------ review rules

    @Test
    void contentEditOfApprovedListingGoesBackToReviewAndIsAudited() {
        Product saved = service.updateBySeller(SELLER, "p-1", named("Indigo mug"));
        assertThat(saved.getStatus()).isEqualTo(ListingService.PENDING);
        assertThat(saved.getName()).isEqualTo("Indigo mug");
        verify(audit).record(eq("PRODUCT_RESUBMITTED"), eq("p-1"), eq("APPROVED -> PENDING after a seller edit"));
    }

    @Test
    void priceOnlyEditOfApprovedListingStaysLive() {
        Product saved = service.updateBySeller(SELLER, "p-1", priced(95.0));
        assertThat(saved.getStatus()).isEqualTo(ListingService.APPROVED);
        assertThat(saved.getPriceCents()).isEqualTo(9500L);
        verify(audit, never()).record(anyString(), anyString(), anyString());
    }

    @Test
    void resendingTheSameContentIsNotAChange() {
        // the SPA always sends the whole form; unchanged values must not trigger a review
        ListingRequest same = new ListingRequest("Blue mug", "Wheel-thrown", new java.math.BigDecimal("80.00"), null, "/img/mug.jpg", "Ceramics", null,
            List.of("clay", "glaze"), null, null, null, null, null, null, true, null, 4, null);
        when(stock.setLevel(SELLER, "p-1", 4)).thenReturn(listing);
        assertThat(service.updateBySeller(SELLER, "p-1", same).getStatus()).isEqualTo(ListingService.APPROVED);
    }

    @Test
    void emptyFormFieldsForMissingValuesAreNotAChange() {
        // the SPA sends '' for an empty description/image; the stored value is null
        listing.setDescription(null);
        listing.setCategoryPath(null);
        ListingRequest r = new ListingRequest("Blue mug", "", null, null, "/img/mug.jpg", "Ceramics", "  ", null, "", null,
            null, null, null, null, null, null, null, null);
        assertThat(service.updateBySeller(SELLER, "p-1", r).getStatus()).isEqualTo(ListingService.APPROVED);
        assertThat(listing.getDescription()).isNull();
    }

    @Test
    void blankTextClearsAValue() {
        ListingRequest r = new ListingRequest(null, " ", null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null);
        Product saved = service.updateBySeller(SELLER, "p-1", r);
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getStatus()).isEqualTo(ListingService.PENDING); // removing the description is a content change
    }

    @Test
    void anyEditOfANeedsChangesListingResubmitsIt() {
        listing.setStatus(ListingService.NEEDS_CHANGES);
        assertThat(service.updateBySeller(SELLER, "p-1", priced(70.0)).getStatus()).isEqualTo(ListingService.PENDING);
        verify(audit).record(eq("PRODUCT_RESUBMITTED"), eq("p-1"), eq("NEEDS_CHANGES -> PENDING after a seller edit"));
    }

    @Test
    void pendingListingStaysPendingWithoutANewAuditEntry() {
        listing.setStatus(ListingService.PENDING);
        assertThat(service.updateBySeller(SELLER, "p-1", named("Other")).getStatus()).isEqualTo(ListingService.PENDING);
        verify(audit, never()).record(anyString(), anyString(), anyString());
    }

    @Test
    void rejectedListingsAreFinal() {
        listing.setStatus(ListingService.REJECTED);
        assertThatThrownBy(() -> service.updateBySeller(SELLER, "p-1", named("Try again")))
            .isInstanceOf(ApiException.class).extracting("status").hasToString("409 CONFLICT");
        verify(products, never()).save(any());
    }

    @Test
    void archivedListingsAreGone() {
        listing.setStatus(Product.ARCHIVED);
        assertThatThrownBy(() -> service.updateBySeller(SELLER, "p-1", named("Back")))
            .isInstanceOf(ApiException.class).extracting("status").hasToString("404 NOT_FOUND");
    }

    @Test
    void otherSellersListingIs404() {
        when(products.findBySellerIdAndId("seller-2", "p-1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateBySeller("seller-2", "p-1", named("Mine now")))
            .isInstanceOf(ApiException.class).extracting("status").hasToString("404 NOT_FOUND");
    }

    @Test
    void blankNameIs400() {
        assertThatThrownBy(() -> service.updateBySeller(SELLER, "p-1", named("  ")))
            .isInstanceOf(ApiException.class).extracting("status").hasToString("400 BAD_REQUEST");
    }

    @ParameterizedTest
    @ValueSource(strings = { "APPROVED", "NEEDS_CHANGES", "PENDING", "REJECTED" })
    void adminEditsNeverChangeTheStatus(String status) {
        listing.setStatus(status);
        assertThat(service.editByAdmin("p-1", named("Cleaned up title")).getStatus()).isEqualTo(status);
        verify(audit).record("PRODUCT_EDIT_BY_ADMIN", "p-1", "Listing content edited by admin");
    }

    // ------------------------------------------------------------------ partial edits keep data

    @Test
    void fieldsMissingFromTheRequestKeepTheirValue() {
        // regression: the seller form never sends materials, coordinates or localPickup; they were wiped
        service.updateBySeller(SELLER, "p-1", priced(90.0));
        assertThat(listing.getMaterials()).containsExactly("clay", "glaze");
        assertThat(listing.getLatitude()).isEqualTo(44.43);
        assertThat(listing.getLongitude()).isEqualTo(26.10);
        assertThat(listing.getLocalPickup()).isTrue();
        assertThat(listing.getHandmade()).isTrue();
        assertThat(listing.getDeliveryEmission()).isEqualTo(EDeliveryEmission.ZERO);
        verify(stock, never()).setLevel(anyString(), anyString(), anyInt());
    }

    @Test
    void stockInAnEditIsForwardedToInventory() {
        ListingRequest r = new ListingRequest(null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, 12, 3);
        when(stock.setLevel(SELLER, "p-1", 12)).thenReturn(listing);
        service.updateBySeller(SELLER, "p-1", r);
        assertThat(listing.getLowStockThreshold()).isEqualTo(3);
        verify(stock).afterListingSaved(listing);
        verify(stock).setLevel(SELLER, "p-1", 12);
    }

    // ------------------------------------------------------------------ create, moderate, archive

    @Test
    void sellerCreatesAPendingListingForThemselves() {
        ListingRequest r = new ListingRequest("Scarf", null, new java.math.BigDecimal("120"), "seller-2", null, null, null, null, null, null, null,
            null, null, null, null, null, 3, null);
        when(stock.register(any(Product.class), eq(3))).thenAnswer(inv -> inv.getArgument(0));
        Product created = service.create(r, new JwtPrincipal(SELLER, "seller", List.of("ROLE_SELLER")));
        assertThat(created.getSellerId()).isEqualTo(SELLER); // a seller can't list for someone else
        assertThat(created.getStatus()).isEqualTo(ListingService.PENDING);
        assertThat(created.getLowStockThreshold()).isEqualTo(Product.DEFAULT_LOW_STOCK_THRESHOLD);
        assertThat(created.getLocalPickup()).isFalse();
    }

    @Test
    void adminMayCreateOnBehalfOfASeller() {
        ListingRequest r = new ListingRequest("Scarf", null, new java.math.BigDecimal("120"), "seller-2", null, null, null, null, null, null, null,
            null, null, null, null, null, null, null);
        when(stock.register(any(Product.class), eq(0))).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.create(r, new JwtPrincipal("admin-1", "admin", List.of("ROLE_ADMIN"))).getSellerId())
            .isEqualTo("seller-2");
    }

    @Test
    void createIsUndoneWhenInventoryFails() {
        when(stock.register(any(Product.class), anyInt())).thenThrow(ApiException.conflict("down"));
        assertThatThrownBy(() -> service.create(namedAndPriced("Scarf", 1.0),
            new JwtPrincipal(SELLER, "seller", List.of("ROLE_SELLER")))).isInstanceOf(ApiException.class);
        verify(products).delete(any(Product.class));
    }

    @Test
    void createNeedsNameAndPrice() {
        JwtPrincipal seller = new JwtPrincipal(SELLER, "seller", List.of("ROLE_SELLER"));
        assertThatThrownBy(() -> service.create(named("Scarf"), seller)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.create(priced(5), seller)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.create(namedAndPriced("Scarf", 1.0), new JwtPrincipal(null, "old", List.of("ROLE_SELLER"))))
            .isInstanceOf(ApiException.class).extracting("status").hasToString("403 FORBIDDEN");
    }

    @Test
    void moderationValidatesTheStatusAndIsAudited() {
        assertThatThrownBy(() -> service.moderate("p-1", "MAYBE", null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.moderate("p-1", null, null)).isInstanceOf(ApiException.class);
        listing.setStatus(ListingService.PENDING);
        assertThat(service.moderate("p-1", "NEEDS_CHANGES", "blurry photo").getStatus()).isEqualTo("NEEDS_CHANGES");
        verify(audit).record("PRODUCT_STATUS", "p-1", "PENDING -> NEEDS_CHANGES, Reason: blurry photo");
        verify(stock).afterListingSaved(listing);
    }

    @Test
    void moderatingAnUnknownListingIs404() {
        when(products.findById("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.moderate("nope", "APPROVED", null))
            .isInstanceOf(ApiException.class).extracting("status").hasToString("404 NOT_FOUND");
    }

    @Test
    void archiveIsASoftDeleteThatDeactivatesStock() {
        service.archive(SELLER, "p-1");
        assertThat(listing.getStatus()).isEqualTo(Product.ARCHIVED);
        verify(stock).afterListingSaved(listing);
        verify(products, never()).delete(any());
    }
}
