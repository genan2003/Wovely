package com.wovely.wovely.services;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wovely.wovely.models.EOrderStatus;
import com.wovely.wovely.models.ManualOrderRequest;
import com.wovely.wovely.models.Order;
import com.wovely.wovely.models.OrderItem;
import com.wovely.wovely.payload.request.OrderInterventionRequest;
import com.wovely.wovely.payload.response.OrderDTO;
import com.wovely.wovely.payload.response.OrderItemDTO;
import com.wovely.wovely.repository.OrderRepository;
import com.wovely.wovely.repository.UserRepository;
import com.wovely.wovely.clients.CatalogClient;
import com.wovely.common.error.ApiException;
import com.wovely.wovely.payload.request.CreateOrderRequest;
import com.wovely.wovely.payload.request.UpdateOrderRequest;
import com.wovely.wovely.services.OrderStatusPolicy.Actor;
import com.wovely.wovely.security.OwnershipGuard;
import com.wovely.wovely.security.services.UserDetailsImpl;


@Service
public class OrderService {

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    AuditService auditService;

    @Autowired
    CatalogClient catalogClient;

    /** Inventory owns stock (PLAN.md 4.2): reserve at checkout/edit, release on cancel/refund/compensation. */
    @Autowired
    com.wovely.wovely.clients.InventoryClient inventoryClient;

    @Autowired
    UserRepository userRepository;

    @Autowired
    OwnershipGuard ownership;

    @Autowired
    EcoCarrierService ecoCarrierService;

    @Autowired
    AddressService addressService;

    @Autowired
    WalletService walletService;

    /** Labels can be bought once the seller accepted the order and until it ships. */
    static final java.util.Set<EOrderStatus> LABELLABLE = java.util.EnumSet.of(EOrderStatus.CONFIRMED, EOrderStatus.PROCESSING);

    public List<OrderDTO> getAllOrders() {
        List<Order> orders = orderRepository.findAll();
        return orders.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    /** Loads an order or throws 404. */
    public Order requireOrder(String id) {
        return orderRepository.findById(id).orElseThrow(() -> ApiException.notFound("Order not found"));
    }

    /** Order visible to its buyer, its seller or an admin; 404 if missing, 403 otherwise. */
    public OrderDTO getOrderForParticipant(String id) {
        Order order = requireOrder(id);
        ownership.requireAnyOf(order.getBuyerId(), order.getSellerId());
        return convertToDto(order);
    }

    /** Loads the order and checks the caller is its buyer (or an admin). */
    public Order requireBuyerOrder(String id) {
        Order order = requireOrder(id);
        ownership.requireAnyOf(order.getBuyerId());
        return order;
    }

    /** Loads the order and checks the caller is its seller (or an admin). */
    public Order requireSellerOrder(String id) {
        Order order = requireOrder(id);
        ownership.requireAnyOf(order.getSellerId());
        return order;
    }

    public OrderDTO getOrderById(String id) {
        Optional<Order> order = orderRepository.findById(id);
        return order.map(this::convertToDto).orElse(null);
    }

    public OrderDTO getOrderByOrderNumber(String orderNumber) {
        Optional<Order> order = orderRepository.findByOrderNumber(orderNumber);
        return order.map(this::convertToDto).orElse(null);
    }

    public List<OrderDTO> getOrdersByBuyerId(String buyerId) {
        List<Order> orders = orderRepository.findByBuyerId(buyerId);
        return orders.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    public List<OrderDTO> getOrdersBySellerId(String sellerId) {
        List<Order> orders = orderRepository.findBySellerId(sellerId);
        return orders.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    public List<OrderDTO> searchOrders(String query) {
        List<Order> orders = orderRepository.findByBuyerNameContainingIgnoreCaseOrSellerNameContainingIgnoreCaseOrOrderNumberContaining(query, query, query);
        return orders.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    public List<OrderDTO> getOrdersByStatus(EOrderStatus status) {
        List<Order> orders = orderRepository.findByStatus(status);
        return orders.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    public List<OrderDTO> getDisputedOrders() {
        List<Order> orders = orderRepository.findByIsDisputedTrue();
        return orders.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    /**
     * Admin intervention (Story 1). All changes are applied to one loaded instance, saved once and
     * audited once (PLAN.md 2.3). Status changes follow {@link OrderStatusPolicy} with the ADMIN actor.
     */
    public OrderDTO applyIntervention(String id, OrderInterventionRequest request) {
        Order order = requireOrder(id);
        EOrderStatus before = order.getStatus();
        String action = request.getAction() == null ? "" : request.getAction();
        String reason = request.getReason();
        switch (action) {
            case "UPDATE_STATUS" -> {
                if (request.getNewStatus() == null) {
                    throw ApiException.badRequest("newStatus is required");
                }
                transition(order, request.getNewStatus(), Actor.ADMIN, reason);
            }
            case "FORCE_REFUND" -> transition(order, EOrderStatus.REFUNDED, Actor.ADMIN, reason);
            case "CANCEL_ORDER" -> transition(order, EOrderStatus.CANCELLED, Actor.ADMIN, reason);
            case "UPDATE_TRACKING" -> {
                if (request.getTrackingNumber() == null || request.getTrackingNumber().isBlank()) {
                    throw ApiException.badRequest("trackingNumber is required");
                }
                order.setTrackingNumber(request.getTrackingNumber().trim());
            }
            case "MARK_DISPUTED" -> {
                transition(order, EOrderStatus.DISPUTED, Actor.ADMIN, reason);
                order.setIsDisputed(true);
                order.setDisputeReason(reason);
            }
            case "RESOLVE_DISPUTE" -> {
                if (!Boolean.TRUE.equals(order.getIsDisputed()) && order.getStatus() != EOrderStatus.DISPUTED) {
                    throw ApiException.conflict("Order is not disputed");
                }
                if (request.getNewStatus() == null) {
                    throw ApiException.badRequest("newStatus is required to resolve a dispute");
                }
                transition(order, request.getNewStatus(), Actor.ADMIN, reason);
                order.setIsDisputed(false);
                order.setDisputeReason(null);
            }
            default -> throw ApiException.badRequest("Unknown action: " + action);
        }
        if (request.getAdminNotes() != null && !request.getAdminNotes().isBlank()) {
            order.setAdminNotes(request.getAdminNotes());
        }
        order.setUpdatedAt(new Date());
        Order saved = orderRepository.save(order);
        auditService.record("ORDER_INTERVENTION", id, "Action: " + action + ", Status: " + before + " -> "
                + saved.getStatus() + (reason != null ? ", Reason: " + reason : ""));
        return convertToDto(saved);
    }

    public OrderDTO regenerateShippingLabel(String id) {
        return generateEcoShippingLabel(id, true);
    }

    /**
     * Status change by a buyer or seller (or an admin using those endpoints). Re-sending the current
     * status is a no-op (optionally updating the ETA); illegal transitions are 409 (PLAN.md 2.4).
     */
    public OrderDTO updateOrderStatus(String orderId, EOrderStatus newStatus, String reason,
            Integer estimatedDeliveryDays, Actor actor) {
        Order order = requireOrder(orderId);
        EOrderStatus before = order.getStatus();
        if (newStatus == before && estimatedDeliveryDays == null) {
            return convertToDto(order);
        }
        if (newStatus != before) {
            transition(order, newStatus, actor, reason);
        }
        if (estimatedDeliveryDays != null) {
            if (estimatedDeliveryDays < 0 || estimatedDeliveryDays > 365) {
                throw ApiException.badRequest("estimatedDeliveryDays must be between 0 and 365");
            }
            order.setEstimatedDeliveryDays(estimatedDeliveryDays);
        }
        order.setUpdatedAt(new Date());
        Order saved = orderRepository.save(order);
        auditService.record("ORDER_STATUS_UPDATE", orderId, actor + ": " + before + " -> " + saved.getStatus()
                + (reason != null ? ", Reason: " + reason : ""));
        return convertToDto(saved);
    }

    /** Applies a status change to {@code order} in memory (no save): policy check, reasons, auto-restock. */
    void transition(Order order, EOrderStatus to, Actor actor, String reason) {
        EOrderStatus from = order.getStatus();
        if (!OrderStatusPolicy.isAllowed(from, to, actor)) {
            throw ApiException.conflict("Cannot change order status from " + from + " to " + to);
        }
        if (to == EOrderStatus.SHIPPED && actor == Actor.SELLER
                && (order.getTrackingNumber() == null || order.getTrackingNumber().isBlank())) {
            throw ApiException.conflict("Generate a shipping label (or get a tracking number) before shipping");
        }
        order.setStatus(to);
        if (to == EOrderStatus.CANCELLED) {
            order.setCancellationReason(reason);
        } else if (to == EOrderStatus.REFUNDED) {
            order.setRefundReason(reason);
        }
        if (OrderStatusPolicy.restocksOn(from, to)) {
            restoreStockForOrder(order);
        }
        if (to == EOrderStatus.CANCELLED || to == EOrderStatus.REFUNDED) {
            walletService.refundRemaining(order);
        } else if (to == EOrderStatus.COMPLETED) {
            walletService.releaseToSeller(order);
        }
    }

    private void reduceStockInServices(String sellerId, String productId, int quantity) {
        inventoryClient.reserve(sellerId, productId, quantity);
    }

    private void restockInServices(String sellerId, String productId, int quantity) {
        inventoryClient.release(sellerId, productId, quantity);
    }

    /**
     * Restores stock for all items in an order (Auto-Restock on Cancellation/Refund).
     */
    private void restoreStockForOrder(Order order) {
        if (order.getItems() == null) return;

        for (OrderItem item : order.getItems()) {
            restockInServices(order.getSellerId(), item.getProductId(), item.getQuantity());
        }
    }

    /**
     * Create a manual order for off-platform sales.
     * This is called by the seller dashboard.
     */
    public OrderDTO createManualOrder(ManualOrderRequest request) {
        Order order = new Order();
        order.setOrderNumber("MAN-" + System.currentTimeMillis());
        order.setBuyerId(null);
        order.setBuyerName(request.getBuyerName());
        order.setSellerId(request.getSellerId());
        order.setSellerName(request.getSellerName());
        order.setShippingAddress(request.getShippingAddress());
        
        // Convert request items to OrderItems
        OrderItem[] items = request.getItems().stream()
            .map(item -> new OrderItem(
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getPrice(),
                item.getImageUrl()
            ))
            .toArray(OrderItem[]::new);
            
        order.setItems(items);
        
        // Calculate total
        double total = request.getItems().stream()
            .mapToDouble(item -> item.getPrice() * item.getQuantity())
            .sum();
        order.setTotalAmount(total);
        
        order.setStatus(EOrderStatus.COMPLETED); // Manual orders are typically completed immediately
        order.setCreatedAt(new Date());
        order.setUpdatedAt(new Date());
        
        // Optional fields
        if (request.getNotes() != null) {
            order.setAdminNotes("Manual Order - " + request.getNotes());
        }

        // Try reducing stock for all items (Real-Time Deduction)
        List<OrderItem> reducedItems = new ArrayList<>();
        try {
            for (OrderItem item : order.getItems()) {
                reduceStockInServices(order.getSellerId(), item.getProductId(), item.getQuantity());
                reducedItems.add(item);
            }
        } catch (Exception e) {
            // Rollback stock reduction for already processed items
            for (OrderItem reducedItem : reducedItems) {
                restockInServices(order.getSellerId(), reducedItem.getProductId(), reducedItem.getQuantity());
            }
            throw e;
        }

        order = orderRepository.save(order);
        return convertToDto(order);
    }

    /**
     * Checkout (real-time stock deduction). Prices, names, seller and totals come from the products
     * service; buyer comes from the authenticated caller; status always starts PENDING (PLAN.md 1.5).
     * All items must belong to one seller (the cart creates one order per seller).
     */
    public OrderDTO createOrder(CreateOrderRequest request, UserDetailsImpl buyer) {
        // Resolve the destination first: no address → 400 before any stock is touched.
        String shippingAddress = addressService.resolveForCheckout(buyer.getId(), request.getAddressId(),
                request.getShippingAddress());
        List<OrderItem> items = new ArrayList<>();
        String sellerId = null;
        double total = 0;
        for (CreateOrderRequest.Item line : request.getItems()) {
            CatalogClient.ProductSnapshot product = catalogClient.getProduct(line.getProductId());
            if (!"APPROVED".equals(product.status())) {
                throw ApiException.badRequest("Product is not available: " + product.name());
            }
            if (product.price() == null || product.price() < 0) {
                throw ApiException.badRequest("Product has no valid price: " + product.name());
            }
            if (sellerId == null) {
                sellerId = product.sellerId();
            } else if (!sellerId.equals(product.sellerId())) {
                throw ApiException.badRequest("All items of an order must belong to the same seller");
            }
            if (buyer.getId().equals(product.sellerId())) {
                throw ApiException.badRequest("You cannot buy your own product");
            }
            items.add(new OrderItem(product.id(), product.name(), line.getQuantity(), product.price(),
                    product.imageUrl(), line.getColor(), line.getSize()));
            total += product.price() * line.getQuantity();
        }

        Order order = new Order();
        Date now = new Date();
        order.setBuyerId(buyer.getId());
        order.setBuyerName(buyer.getUsername());
        order.setSellerId(sellerId);
        order.setSellerName(userRepository.findById(sellerId).map(u -> u.getUsername()).orElse("Unknown seller"));
        order.setItems(items.toArray(OrderItem[]::new));
        order.setTotalAmount(Math.round(total * 100.0) / 100.0);
        order.setStatus(EOrderStatus.PENDING);
        order.setShippingAddress(shippingAddress);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.setOrderNumber("ORD-" + System.currentTimeMillis() + "-"
                + buyer.getId().substring(0, Math.min(4, buyer.getId().length())).toUpperCase());

        // Deduct stock first; compensate already-deducted lines if any line fails
        List<OrderItem> reduced = new ArrayList<>();
        try {
            for (OrderItem item : order.getItems()) {
                reduceStockInServices(sellerId, item.getProductId(), item.getQuantity());
                reduced.add(item);
            }
            // Need the id for the ledger line: save first, then charge; undo both if the buyer can't pay.
            order = orderRepository.save(order);
            try {
                walletService.chargeOrder(order);
            } catch (RuntimeException payment) {
                orderRepository.delete(order);
                throw payment;
            }
        } catch (RuntimeException e) {
            for (OrderItem item : reduced) {
                restockInServices(sellerId, item.getProductId(), item.getQuantity());
            }
            throw e;
        }
        return convertToDto(orderRepository.save(order));
    }

    /**
     * Seller edit before processing (PLAN.md 2.5): replaces the item list and/or address,
     * reconciling stock per product (increase → deduct, decrease/removal → restock) and recomputing
     * the total from stored/server prices. Deductions run first and are compensated on failure.
     */
    public OrderDTO updateOrderPreProcessing(String orderId, UpdateOrderRequest request) {
        Order order = requireOrder(orderId);
        if (!OrderStatusPolicy.isEditable(order.getStatus())) {
            throw ApiException.conflict("Order is locked and cannot be edited in its current status: " + order.getStatus());
        }
        List<String> changes = new ArrayList<>();
        if (request.getItems() != null) {
            List<CreateOrderRequest.Item> previous = java.util.Arrays.stream(order.getItems())
                    .map(i -> new CreateOrderRequest.Item(i.getProductId(), i.getQuantity())).toList();
            changes.add(reconcileItems(order, request.getItems()));
            try {
                walletService.settleEdit(order);
            } catch (RuntimeException e) {
                reconcileItems(order, previous); // put the stock back as it was
                throw e;
            }
        }
        if (request.getShippingAddress() != null && !request.getShippingAddress().equals(order.getShippingAddress())) {
            order.setShippingAddress(request.getShippingAddress());
            changes.add("shippingAddress updated");
        }
        order.setUpdatedAt(new Date());
        Order saved = orderRepository.save(order);
        if (!changes.isEmpty()) {
            auditService.record("ORDER_EDIT", orderId, String.join("; ", changes));
        }
        return convertToDto(saved);
    }

    private String reconcileItems(Order order, List<CreateOrderRequest.Item> wanted) {
        if (wanted.isEmpty()) {
            throw ApiException.badRequest("An order needs at least one item; cancel it instead");
        }
        String sellerId = order.getSellerId();
        java.util.Map<String, OrderItem> current = new java.util.LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            current.put(item.getProductId(), item);
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<OrderItem> next = new ArrayList<>();
        java.util.Map<String, Integer> deductions = new java.util.LinkedHashMap<>();
        java.util.Map<String, Integer> restocks = new java.util.LinkedHashMap<>();

        for (CreateOrderRequest.Item line : wanted) {
            if (!seen.add(line.getProductId())) {
                throw ApiException.badRequest("Duplicate product in order: " + line.getProductId());
            }
            OrderItem item = current.get(line.getProductId());
            int oldQty = item != null ? item.getQuantity() : 0;
            if (item == null) {
                CatalogClient.ProductSnapshot p = catalogClient.getProduct(line.getProductId());
                if (!"APPROVED".equals(p.status()) || !sellerId.equals(p.sellerId())) {
                    throw ApiException.badRequest("Product cannot be added to this order: " + p.name());
                }
                item = new OrderItem(p.id(), p.name(), 0, p.price(), p.imageUrl());
            }
            int delta = line.getQuantity() - oldQty;
            if (delta > 0) deductions.put(item.getProductId(), delta);
            if (delta < 0) restocks.put(item.getProductId(), -delta);
            item.setQuantity(line.getQuantity());
            if (line.getColor() != null) item.setColor(line.getColor());
            if (line.getSize() != null) item.setSize(line.getSize());
            next.add(item);
        }
        for (OrderItem old : current.values()) {
            if (!seen.contains(old.getProductId())) {
                restocks.put(old.getProductId(), old.getQuantity());
            }
        }

        // 1) deductions can fail (409) → compensate the ones already applied
        java.util.Map<String, Integer> applied = new java.util.LinkedHashMap<>();
        try {
            for (var d : deductions.entrySet()) {
                inventoryClient.reserve(sellerId, d.getKey(), d.getValue());
                applied.put(d.getKey(), d.getValue());
            }
        } catch (RuntimeException e) {
            applied.forEach((pid, qty) -> inventoryClient.release(sellerId, pid, qty));
            throw e;
        }
        // 2) restocks never throw (logged by CatalogClient)
        restocks.forEach((pid, qty) -> inventoryClient.release(sellerId, pid, qty));

        order.setItems(next.toArray(OrderItem[]::new));
        double total = next.stream().mapToDouble(i -> i.getPrice() * i.getQuantity()).sum();
        order.setTotalAmount(Math.round(total * 100.0) / 100.0);
        return "items updated (deducted " + deductions + ", restocked " + restocks + ")";
    }

    /**
     * "Ship with Platform" (PLAN.md 3.6): buy a prepaid label from the lowest-CO₂ carrier that covers the
     * route; sets the tracking number so the seller can ship. Seller: once per order (409 when a label
     * exists). Admin: {@code regenerate} replaces it (e.g. lost label).
     */
    public OrderDTO generateEcoShippingLabel(String orderId, boolean regenerate) {
        Order order = requireOrder(orderId);
        if (!LABELLABLE.contains(order.getStatus())) {
            throw ApiException.conflict("Shipping labels are only available for CONFIRMED or PROCESSING orders (order is "
                    + order.getStatus() + ")");
        }
        if (order.getEcoShippingLabel() != null && !regenerate) {
            throw ApiException.conflict("This order already has a shipping label");
        }
        String previous = order.getTrackingNumber();
        EcoCarrierService.ShippingLabel label = ecoCarrierService.generateLabel(order.getOrderNumber(),
                order.getShippingAddress());
        order.setEcoShippingLabel(label.text());
        order.setTrackingNumber(label.trackingNumber());
        order.setCarrierName(label.carrier().name());
        order.setCarrierMethod(label.carrier().method());
        order.setShippingDistanceKm(label.distanceKm());
        order.setEstimatedCo2Kg(label.estimatedCo2Kg());
        order.setLabelGeneratedAt(new Date());
        order.setUpdatedAt(new Date());
        Order saved = orderRepository.save(order);
        auditService.record(regenerate ? "SHIPPING_LABEL_REGENERATED" : "SHIPPING_LABEL_GENERATED", orderId,
                label.carrier().name() + ", tracking " + label.trackingNumber()
                        + (previous != null ? " (was " + previous + ")" : "") + ", ~" + label.estimatedCo2Kg() + " kg CO2");
        return convertToDto(saved);
    }

    private OrderDTO convertToDto(Order order) {
        List<OrderItemDTO> items = null;
        if (order.getItems() != null) {
            items = java.util.Arrays.stream(order.getItems())
                .map(item -> new OrderItemDTO(
                    item.getProductId(),
                    item.getProductName(),
                    item.getQuantity(),
                    item.getPrice(),
                    item.getImageUrl(),
                    item.isReturned(),
                    item.isReviewed()
                ))
                .collect(Collectors.toList());
        }
        
        return new OrderDTO(
            order.getId(),
            order.getOrderNumber(),
            order.getBuyerId(),
            order.getBuyerName(),
            order.getSellerId(),
            order.getSellerName(),
            items,
            order.getTotalAmount(),
            order.getStatus(),
            order.getCreatedAt(),
            order.getUpdatedAt(),
            order.getShippingAddress(),
            order.getTrackingNumber(),
            order.getRefundReason(),
            order.getCancellationReason(),
            order.getAdminNotes(),
            order.getIsDisputed(),
            order.getDisputeReason(),
            order.getEcoShippingLabel(),
            order.getEstimatedDeliveryDays(),
            order.isReviewed()
        ).withRefundedAmount(order.getRefundedAmount())
         .withShipping(order.getCarrierName(), order.getCarrierMethod(), order.getShippingDistanceKm(),
                order.getEstimatedCo2Kg(), order.getLabelGeneratedAt());
    }
}
