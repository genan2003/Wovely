package com.wovely.wovely.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.wovely.wovely.clients.CatalogClient;
import com.wovely.wovely.clients.InventoryClient;
import com.wovely.wovely.models.EAccountStatus;
import com.wovely.wovely.models.EOrderStatus;
import com.wovely.wovely.models.Order;
import com.wovely.wovely.models.OrderStatusChange;
import com.wovely.wovely.payload.request.OrderInterventionRequest;
import com.wovely.wovely.payload.response.OrderDTO;
import com.wovely.wovely.payload.response.OrderTimelineEntryDTO;
import com.wovely.wovely.repository.OrderRepository;
import com.wovely.wovely.security.services.UserDetailsImpl;
import com.wovely.wovely.services.OrderStatusPolicy.Actor;
import com.wovely.wovely.support.Orders;

/** PLAN.md 7.1: every status change is recorded once, with actor and reason; legacy orders get a derived timeline. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderTimelineTest {

    @Mock OrderRepository orderRepository;
    @Mock CatalogClient catalogClient;
    @Mock InventoryClient inventoryClient;
    @Mock AuditService auditService;
    @Mock EcoCarrierService ecoCarrierService;
    @Mock AddressService addressService;
    @Mock WalletService walletService;
    @InjectMocks OrderService service;

    @BeforeEach
    void setUp() {
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void signIn(String id, String role) {
        var user = new UserDetailsImpl(id, id, id + "@x.ro", "pw", List.of(new SimpleGrantedAuthority(role)),
                EAccountStatus.ACTIVE, null);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    /** Entries written by a real change (the fixtures have no history, so the first change also stores derived steps). */
    private static List<OrderStatusChange> recorded(Order o) {
        return o.getStatusHistory().stream().filter(c -> c.actorRole() != null).toList();
    }

    @Test
    void eachTransitionAppendsOneEntryWithActorAndReason() {
        Order o = Orders.order("o-1", "buyer-1", "seller-1", EOrderStatus.PENDING);
        signIn("seller-1", "ROLE_SELLER");
        service.transition(o, EOrderStatus.CONFIRMED, Actor.SELLER, null);
        service.transition(o, EOrderStatus.PROCESSING, Actor.SELLER, "  ");
        signIn("buyer-1", "ROLE_USER");
        assertThatThrownBy(() -> service.transition(o, EOrderStatus.CANCELLED, Actor.BUYER, "changed my mind"))
                .hasMessageContaining("Cannot change"); // buyers can't cancel PROCESSING
        signIn("seller-1", "ROLE_SELLER");
        service.transition(o, EOrderStatus.CANCELLED, Actor.SELLER, " out of wool ");

        assertThat(recorded(o))
                .extracting(OrderStatusChange::from, OrderStatusChange::to, OrderStatusChange::actorRole,
                        OrderStatusChange::actorId, OrderStatusChange::reason)
                .containsExactly(
                        tuple(EOrderStatus.PENDING, EOrderStatus.CONFIRMED, "SELLER", "seller-1", null),
                        tuple(EOrderStatus.CONFIRMED, EOrderStatus.PROCESSING, "SELLER", "seller-1", null),
                        tuple(EOrderStatus.PROCESSING, EOrderStatus.CANCELLED, "SELLER", "seller-1", "out of wool"));
    }

    @Test
    void rejectedTransitionRecordsNothing() {
        Order o = Orders.order("o-1", "buyer-1", "seller-1", EOrderStatus.PROCESSING);
        signIn("seller-1", "ROLE_SELLER");
        assertThatThrownBy(() -> service.transition(o, EOrderStatus.SHIPPED, Actor.SELLER, null)); // no tracking yet
        assertThat(o.getStatusHistory()).isEmpty();
        assertThat(o.getStatus()).isEqualTo(EOrderStatus.PROCESSING);
    }

    @Test
    void systemChangesAreAttributedToSystemEvenWithACaller() {
        Order o = Orders.order("o-1", "buyer-1", "seller-1", EOrderStatus.DELIVERED);
        signIn("buyer-1", "ROLE_USER"); // e.g. the buyer's return completes the refund
        service.transition(o, EOrderStatus.REFUNDED, Actor.SYSTEM, "All items returned");
        assertThat(recorded(o)).singleElement()
                .satisfies(c -> {
                    assertThat(c.actorRole()).isEqualTo("SYSTEM");
                    assertThat(c.actorId()).isEqualTo("system");
                });
    }

    @Test
    void deliveredEntryAndDeliveredAtShareOneTimestamp() {
        Order o = Orders.order("o-1", "buyer-1", "seller-1", EOrderStatus.SHIPPED);
        signIn("buyer-1", "ROLE_USER");
        service.transition(o, EOrderStatus.DELIVERED, Actor.BUYER, null);
        assertThat(recorded(o).get(0).at()).isEqualTo(o.getDeliveredAt());
    }

    @Test
    void adminInterventionIsInTheTimelineButTheAdminIdIsNotExposed() {
        Order o = Orders.order("o-1", "buyer-1", "seller-1", EOrderStatus.SHIPPED);
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(o));
        signIn("admin-1", "ROLE_ADMIN");
        OrderInterventionRequest r = new OrderInterventionRequest();
        r.setAction("MARK_DISPUTED");
        r.setReason("Parcel lost");

        OrderDTO dto = service.applyIntervention("o-1", r);

        assertThat(recorded(o)).singleElement()
                .extracting(OrderStatusChange::actorId).isEqualTo("admin-1"); // stored for the audit view
        assertThat(dto.getTimeline()).last().satisfies(e -> {
            assertThat(e.status()).isEqualTo(EOrderStatus.DISPUTED);
            assertThat(e.actor()).isEqualTo("ADMIN");
            assertThat(e.reason()).isEqualTo("Parcel lost");
            assertThat(e.inferred()).isFalse();
        });
        assertThat(OrderTimelineEntryDTO.class.getRecordComponents())
                .extracting(c -> c.getName()).doesNotContain("actorId");
    }

    @Test
    void manualSaleStartsWithACompletedEntry() {
        var req = new com.wovely.wovely.models.ManualOrderRequest();
        req.setSellerId("seller-1");
        req.setSellerName("seller");
        req.setBuyerName("Walk-in");
        var item = new com.wovely.wovely.models.ManualOrderItemRequest();
        item.setProductId("p-1");
        item.setProductName("Basket");
        item.setQuantity(1);
        item.setPrice(new java.math.BigDecimal("10.00"));
        req.setItems(List.of(item));
        signIn("seller-1", "ROLE_SELLER");

        OrderDTO dto = service.createManualOrder(req);

        assertThat(dto.getTimeline()).singleElement().satisfies(e -> {
            assertThat(e.from()).isNull();
            assertThat(e.status()).isEqualTo(EOrderStatus.COMPLETED);
            assertThat(e.actor()).isEqualTo("SELLER");
        });
    }

    // ------------------------------------------------------------- orders older than the timeline

    @Test
    void legacyOrderTimelineIsDerivedFromStoredDates() {
        Order o = Orders.order("o-1", "buyer-1", "seller-1", EOrderStatus.COMPLETED);
        Date created = new Date(1_000_000L), delivered = new Date(2_000_000L), updated = new Date(3_000_000L);
        o.setCreatedAt(created);
        o.setDeliveredAt(delivered);
        o.setUpdatedAt(updated);
        o.setStatusHistory(null); // as loaded from a document without status_history

        assertThat(o.timeline())
                .extracting(OrderStatusChange::from, OrderStatusChange::to, OrderStatusChange::at, OrderStatusChange::actorRole)
                .containsExactly(
                        tuple(null, EOrderStatus.PENDING, created, null),
                        tuple(EOrderStatus.PENDING, EOrderStatus.DELIVERED, delivered, null),
                        tuple(EOrderStatus.DELIVERED, EOrderStatus.COMPLETED, updated, null));
        assertThat(OrderTimelineEntryDTO.of(o.timeline().get(0)).inferred()).isTrue();
    }

    @Test
    void firstChangeOfALegacyOrderKeepsTheDerivedSteps() {
        Order o = Orders.order("o-1", "buyer-1", "seller-1", EOrderStatus.SHIPPED);
        o.setCreatedAt(new Date(1_000L));
        o.setUpdatedAt(new Date(2_000L));
        signIn("buyer-1", "ROLE_USER");
        service.transition(o, EOrderStatus.DELIVERED, Actor.BUYER, null);
        service.transition(o, EOrderStatus.COMPLETED, Actor.BUYER, null);

        assertThat(o.getStatusHistory())
                .extracting(OrderStatusChange::from, OrderStatusChange::to, OrderStatusChange::actorRole)
                .containsExactly(
                        tuple(null, EOrderStatus.PENDING, null),
                        tuple(EOrderStatus.PENDING, EOrderStatus.SHIPPED, null),
                        tuple(EOrderStatus.SHIPPED, EOrderStatus.DELIVERED, "BUYER"),
                        tuple(EOrderStatus.DELIVERED, EOrderStatus.COMPLETED, "BUYER"));
        assertThat(o.getStatusHistory().get(1).at()).isEqualTo(new Date(2_000L));
    }

    @Test
    void legacyCancelledOrderKeepsItsReason() {
        Order o = Orders.order("o-1", "buyer-1", "seller-1", EOrderStatus.CANCELLED);
        o.setCancellationReason("Out of stock");
        assertThat(o.timeline()).extracting(OrderStatusChange::to, OrderStatusChange::reason)
                .containsExactly(tuple(EOrderStatus.PENDING, null), tuple(EOrderStatus.CANCELLED, "Out of stock"));
    }

    @Test
    void legacyPendingOrderHasOneEntry() {
        Order o = Orders.order("o-1", "buyer-1", "seller-1", EOrderStatus.PENDING);
        assertThat(o.timeline()).singleElement().extracting(OrderStatusChange::to).isEqualTo(EOrderStatus.PENDING);
    }

    @Test
    void legacyDeliveredOrderUsesDeliveredAtOnce() {
        Order o = Orders.order("o-1", "buyer-1", "seller-1", EOrderStatus.DELIVERED);
        o.setDeliveredAt(new Date(5_000L));
        assertThat(o.timeline()).extracting(OrderStatusChange::to, OrderStatusChange::at)
                .containsExactly(tuple(EOrderStatus.PENDING, o.getCreatedAt()), tuple(EOrderStatus.DELIVERED, new Date(5_000L)));
    }
}
