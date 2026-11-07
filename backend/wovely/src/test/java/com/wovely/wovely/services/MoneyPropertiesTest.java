package com.wovely.wovely.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.wovely.wovely.clients.CatalogClient;
import com.wovely.wovely.clients.CatalogClient.ProductSnapshot;
import com.wovely.wovely.clients.InventoryClient;
import com.wovely.wovely.models.EAccountStatus;
import com.wovely.wovely.models.EDeliveryOption;
import com.wovely.wovely.models.Order;
import com.wovely.wovely.models.OrderItem;
import com.wovely.wovely.models.User;
import com.wovely.wovely.payload.request.CreateOrderRequest;
import com.wovely.wovely.payload.response.OrderDTO;
import com.wovely.wovely.repository.OrderRepository;
import com.wovely.wovely.repository.UserRepository;
import com.wovely.wovely.security.services.UserDetailsImpl;

/**
 * PLAN.md 7.5 — properties over random carts (seeded, so failures reproduce): the order total is exactly
 * Σ unit price × quantity + delivery fee, the wallet is charged exactly that, the decimal shown to clients is the
 * same amount, and any split of the items into returns adds back up to the items total.
 *
 * <p>jqwik would be the natural tool, but its engine targets JUnit Platform 1.x and this build runs JUnit 6
 * (Spring Boot 4); a seeded generator with {@code @ParameterizedTest} gives the same coverage without it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MoneyPropertiesTest {

    static final long SEED = 7_05L;
    static final int CASES = 500;

    @Mock OrderRepository orderRepository;
    @Mock CatalogClient catalogClient;
    @Mock InventoryClient inventoryClient;
    @Mock AuditService auditService;
    @Mock EcoCarrierService ecoCarrierService;
    @Mock AddressService addressService;
    @Mock WalletService walletService;
    @Mock UserRepository userRepository;
    @InjectMocks OrderService service;

    /** A random cart: unit prices 0.01–999.99 RON, quantities 1–25, 1–6 lines, any delivery option. */
    record Cart(List<Long> unitCents, List<Integer> quantities, EDeliveryOption delivery) {
        long expectedTotal() {
            long sum = delivery.feeCents();
            for (int i = 0; i < unitCents.size(); i++) {
                sum += unitCents.get(i) * quantities.get(i);
            }
            return sum;
        }
    }

    static Stream<Arguments> carts() {
        Random random = new Random(SEED);
        EDeliveryOption[] options = EDeliveryOption.values();
        return IntStream.range(0, CASES).mapToObj(n -> {
            int lines = 1 + random.nextInt(6);
            List<Long> prices = new ArrayList<>();
            List<Integer> quantities = new ArrayList<>();
            for (int i = 0; i < lines; i++) {
                // bias towards x.x5 / x.99 style prices, the ones binary doubles get wrong
                long cents = random.nextBoolean() ? 1 + random.nextInt(99_999) : (1 + random.nextInt(999)) * 100L - 1;
                prices.add(cents);
                quantities.add(1 + random.nextInt(25));
            }
            return Arguments.of(n, new Cart(prices, quantities, options[random.nextInt(options.length)]));
        });
    }

    @ParameterizedTest(name = "cart #{0}")
    @MethodSource("carts")
    void checkoutChargesExactlyItemsPlusDelivery(int n, Cart cart) {
        List<CreateOrderRequest.Item> lines = new ArrayList<>();
        for (int i = 0; i < cart.unitCents().size(); i++) {
            String id = "p-" + i;
            when(catalogClient.getProduct(id))
                    .thenReturn(new ProductSnapshot(id, "Item " + i, cart.unitCents().get(i), "seller-1", "", "APPROVED", 99));
            lines.add(new CreateOrderRequest.Item(id, cart.quantities().get(i)));
        }
        when(addressService.resolveForCheckout(anyString(), any(), any())).thenReturn("Str. Test 1");
        when(userRepository.findById("seller-1")).thenReturn(Optional.of(new User()));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(lines);
        request.setDeliveryOption(cart.delivery());
        var buyer = new UserDetailsImpl("buyer-1", "buyer", "b@x.ro", "pw", List.of(new SimpleGrantedAuthority("ROLE_USER")),
                EAccountStatus.ACTIVE, null);

        OrderDTO dto = service.createOrder(request, buyer);

        long expected = cart.expectedTotal();
        assertThat(dto.getTotalCents()).isEqualTo(expected);
        assertThat(dto.getTotalAmount()).isEqualTo(BigDecimal.valueOf(expected, 2)); // same amount, scale 2
        assertThat(dto.getTotalAmount().scale()).isEqualTo(2);
        verify(walletService).chargeOrder(argThat(o -> o.getTotalCents() == expected));
    }

    @ParameterizedTest(name = "cart #{0}")
    @MethodSource("carts")
    void anySplitIntoReturnsAddsBackToTheItemsTotal(int n, Cart cart) {
        Order order = new Order();
        OrderItem[] items = new OrderItem[cart.unitCents().size()];
        for (int i = 0; i < items.length; i++) {
            items[i] = new OrderItem("p-" + i, "Item", cart.quantities().get(i), cart.unitCents().get(i), "");
        }
        order.setItems(items);
        order.setDeliveryFeeCents(cart.delivery().feeCents());
        order.recomputeTotal();

        Random split = new Random(SEED + n);
        long returnedA = 0, returnedB = 0;
        for (OrderItem item : items) {
            if (split.nextBoolean()) returnedA += item.lineTotalCents(); else returnedB += item.lineTotalCents();
        }
        assertThat(returnedA + returnedB).isEqualTo(order.itemsTotalCents());
        assertThat(order.getTotalCents() - order.itemsTotalCents()).isEqualTo(cart.delivery().feeCents());
    }
}
