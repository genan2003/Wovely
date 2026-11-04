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
import com.wovely.wovely.exception.ApiException;
import com.wovely.wovely.payload.request.CreateOrderRequest;
import com.wovely.wovely.security.OwnershipGuard;
import com.wovely.wovely.security.services.UserDetailsImpl;

import com.wovely.wovely.models.AuditLog;
import com.wovely.wovely.repository.AuditLogRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

@Service
public class OrderService {

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Autowired
    CatalogClient catalogClient;

    @Autowired
    UserRepository userRepository;

    @Autowired
    OwnershipGuard ownership;

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

    public OrderDTO applyIntervention(String id, OrderInterventionRequest request) {
        OrderDTO result = handleIntervention(id, request);
        if (result != null) {
            logAuditAction("ORDER_INTERVENTION", id, "Action: " + request.getAction() + ", Reason: " + request.getReason());
        }
        return result;
    }

    private void logAuditAction(String type, String targetId, String details) {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            String username = "system";
            String adminId = "system";
            
            if (principal instanceof UserDetailsImpl me) {
                username = me.getUsername();
                adminId = me.getId();
            } else if (principal instanceof UserDetails) {
                username = ((UserDetails)principal).getUsername();
            }
            
            AuditLog log = new AuditLog(adminId, username, type, targetId, details);
            auditLogRepository.save(log);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(OrderService.class).error("Audit logging failed: {}", e.getMessage());
        }
    }

    public OrderDTO regenerateShippingLabel(String id) {
        return generateEcoShippingLabel(id);
    }

    /**
     * Admin/System intervention for an order.
     */
    public OrderDTO handleIntervention(String orderId, OrderInterventionRequest request) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            String action = request.getAction();
            
            if ("UPDATE_STATUS".equals(action)) {
                if (request.getNewStatus() != null) {
                    return updateOrderStatus(orderId, request.getNewStatus(), request.getReason(), request.getAdminNotes());
                }
            } else if ("FORCE_REFUND".equals(action)) {
                return updateOrderStatus(orderId, EOrderStatus.REFUNDED, request.getReason(), request.getAdminNotes());
            } else if ("CANCEL_ORDER".equals(action)) {
                return updateOrderStatus(orderId, EOrderStatus.CANCELLED, request.getReason(), request.getAdminNotes());
            } else if ("UPDATE_TRACKING".equals(action)) {
                order.setTrackingNumber(request.getTrackingNumber());
                if (request.getAdminNotes() != null) order.setAdminNotes(request.getAdminNotes());
                order.setUpdatedAt(new Date());
                orderRepository.save(order);
                return convertToDto(order);
            } else if ("MARK_DISPUTED".equals(action)) {
                order.setIsDisputed(true);
                order.setDisputeReason(request.getReason());
                return updateOrderStatus(orderId, EOrderStatus.DISPUTED, request.getReason(), request.getAdminNotes());
            } else if ("RESOLVE_DISPUTE".equals(action)) {
                order.setIsDisputed(false);
                order.setDisputeReason(null);
                if (request.getNewStatus() != null) {
                    return updateOrderStatus(orderId, request.getNewStatus(), request.getReason(), request.getAdminNotes());
                }
            }
            
            return convertToDto(order);
        }
        
        return null;
    }

    /**
     * Centralized method to update order status and handle side effects like auto-restocking.
     */
    public OrderDTO updateOrderStatus(String orderId, EOrderStatus newStatus, String reason, String adminNotes) {
        return updateOrderStatus(orderId, newStatus, reason, adminNotes, null);
    }

    public OrderDTO updateOrderStatus(String orderId, EOrderStatus newStatus, String reason, String adminNotes, Integer estimatedDeliveryDays) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            EOrderStatus oldStatus = order.getStatus();
            
            if (oldStatus == newStatus && estimatedDeliveryDays == null) {
                return convertToDto(order);
            }

            order.setStatus(newStatus);
            
            logAuditAction("ORDER_STATUS_UPDATE", orderId, "New Status: " + newStatus + (reason != null ? ", Reason: " + reason : ""));
            
            if (EOrderStatus.CANCELLED.equals(newStatus)) {
                order.setCancellationReason(reason);
            } else if (EOrderStatus.REFUNDED.equals(newStatus)) {
                order.setRefundReason(reason);
            }

            if (estimatedDeliveryDays != null) {
                order.setEstimatedDeliveryDays(estimatedDeliveryDays);
            }
            
            // Auto-Restock on Cancellation or Refund
            if ((newStatus == EOrderStatus.CANCELLED || newStatus == EOrderStatus.REFUNDED) && 
                (oldStatus != EOrderStatus.CANCELLED && oldStatus != EOrderStatus.REFUNDED)) {
                restoreStockForOrder(order);
            }
            
            if (adminNotes != null && !adminNotes.isEmpty()) {
                order.setAdminNotes(adminNotes);
            }
            
            order.setUpdatedAt(new Date());
            orderRepository.save(order);
            
            return convertToDto(order);
        }
        
        return null;
    }

    private void reduceStockInServices(String sellerId, String productId, int quantity) {
        catalogClient.reduceStock(sellerId, productId, quantity);
    }

    private void restockInServices(String sellerId, String productId, int quantity) {
        catalogClient.restock(sellerId, productId, quantity);
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
        order.setShippingAddress(request.getShippingAddress());
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
        } catch (RuntimeException e) {
            for (OrderItem item : reduced) {
                restockInServices(sellerId, item.getProductId(), item.getQuantity());
            }
            throw e;
        }
        return convertToDto(orderRepository.save(order));
    }

    /**
     * Update order items (variations) or shipping address during pre-processing.
     * Locked once status is PROCESSING or beyond.
     */
    public OrderDTO updateOrderPreProcessing(String orderId, Order updatedOrderData) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            EOrderStatus currentStatus = order.getStatus();
            
            // Lock if status is beyond CONFIRMED (i.e., PROCESSING, SHIPPED, etc.)
            if (currentStatus != EOrderStatus.PENDING && currentStatus != EOrderStatus.CONFIRMED) {
                throw ApiException.conflict("Order is locked and cannot be edited in its current status: " + currentStatus);
            }
            
            // Update items if provided
            if (updatedOrderData.getItems() != null) {
                // This is complex - would need to handle stock differences.
                // For MVP, we'll just allow updating shipping address.
                order.setItems(updatedOrderData.getItems());
            }
            
            if (updatedOrderData.getShippingAddress() != null) {
                order.setShippingAddress(updatedOrderData.getShippingAddress());
            }
            
            order.setUpdatedAt(new Date());
            orderRepository.save(order);
            return convertToDto(order);
        }
        
        return null;
    }

    /**
     * Generate an automated eco-shipping label.
     */
    public OrderDTO generateEcoShippingLabel(String orderId) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            
            // In a real app, this would call an external API
            String label = "ECO-" + order.getOrderNumber() + "-LABEL";
            order.setEcoShippingLabel(label);
            order.setUpdatedAt(new Date());
            orderRepository.save(order);
            
            return convertToDto(order);
        }
        return null;
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
        );
    }
}
