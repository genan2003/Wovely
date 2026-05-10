package com.wovely.wovely.services;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.wovely.wovely.models.EOrderStatus;
import com.wovely.wovely.models.ManualOrderRequest;
import com.wovely.wovely.models.Order;
import com.wovely.wovely.models.OrderItem;
import com.wovely.wovely.payload.request.OrderInterventionRequest;
import com.wovely.wovely.payload.response.OrderDTO;
import com.wovely.wovely.payload.response.OrderItemDTO;
import com.wovely.wovely.repository.OrderRepository;

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
    RestTemplate restTemplate;

    @Autowired
    EcoCarrierService ecoCarrierService;

    @org.springframework.beans.factory.annotation.Value("${products.api.url:http://localhost:8082/api/products}")
    private String PRODUCTS_API;

    @org.springframework.beans.factory.annotation.Value("${inventory.api.url:http://localhost:8083/api/inventory}")
    private String INVENTORY_API;

    public List<OrderDTO> getAllOrders() {
        List<Order> orders = orderRepository.findAll();
        return orders.stream().map(this::convertToDto).collect(Collectors.toList());
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
            
            if (principal instanceof UserDetails) {
                username = ((UserDetails)principal).getUsername();
                // In a real app, you'd fetch the User object to get the ID. 
                // For this MVP, we'll just log the username.
            }
            
            AuditLog log = new AuditLog(adminId, username, type, targetId, details);
            auditLogRepository.save(log);
        } catch (Exception e) {
            System.err.println("Audit logging failed: " + e.getMessage());
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

    /**
     * Reduce stock for an item in both products and inventory services (Real-Time Deduction).
     */
    private void reduceStockInServices(String sellerId, String productId, int quantity) {
        String productsUrl = PRODUCTS_API + "/seller/" + sellerId + "/product/" + productId + "/reduce-stock";
        String inventoryUrl = INVENTORY_API + "/seller/" + sellerId + "/product/" + productId + "/reduce-stock";

        try {
            // Try reducing in products service first
            restTemplate.postForEntity(productsUrl, java.util.Map.of("quantity", quantity), Object.class);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // Extract the error message from the response body if available
            String responseBody = e.getResponseBodyAsString();
            if (responseBody.contains("Not enough stock available")) {
                // Try to extract available quantity from response if possible
                String detail = "";
                try {
                    // Simple parsing of "available":X in the JSON response
                    if (responseBody.contains("\"available\":")) {
                        int start = responseBody.indexOf("\"available\":") + 12;
                        int end = responseBody.indexOf(",", start);
                        if (end == -1) end = responseBody.indexOf("}", start);
                        if (end != -1) {
                            detail = " (Available: " + responseBody.substring(start, end).trim() + ", Requested: " + quantity + ")";
                        }
                    }
                } catch (Exception ex) {
                    // Fallback to simple message
                }
                throw new RuntimeException("Insufficient stock for product ID: " + productId + detail);
            } else if (responseBody.contains("Product not found")) {
                throw new RuntimeException("Product not found: " + productId);
            }
            throw new RuntimeException("Stock deduction failed: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Failed to reach products service: " + e.getMessage());
        }

        // Then try in inventory service (best effort sync)
        try {
            restTemplate.postForEntity(inventoryUrl, java.util.Map.of("quantity", quantity), Object.class);
        } catch (Exception e) {
            System.err.println("Inventory service sync failed for product " + productId + ": " + e.getMessage());
        }
    }

    /**
     * Restores stock for an item in both products and inventory services (Auto-Restock).
     */
    private void restockInServices(String sellerId, String productId, int quantity) {
        try {
            String productsUrl = PRODUCTS_API + "/seller/" + sellerId + "/product/" + productId + "/restock";
            String inventoryUrl = INVENTORY_API + "/seller/" + sellerId + "/product/" + productId + "/restock";

            restTemplate.postForEntity(productsUrl, java.util.Map.of("quantity", quantity), Object.class);
            
            try {
                restTemplate.postForEntity(inventoryUrl, java.util.Map.of("quantity", quantity), Object.class);
            } catch (Exception e) {
                System.err.println("Inventory service restock sync failed for product " + productId + ": " + e.getMessage());
            }
        } catch (Exception e) {
            System.err.println("Failed to restock in products service for product " + productId + ": " + e.getMessage());
        }
    }

    /**
     * Restores stock for all items in an order (Auto-Restock on Cancellation/Refund).
     */
    private void restoreStockForOrder(Order order) {
        if (order.getItems() == null) return;

        System.out.println("Restoring stock for order: " + order.getOrderNumber());
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
     * Create a regular order (Real-Time Deduction).
     * This is called when a buyer completes checkout.
     */
    public OrderDTO createOrder(Order order) {
        if (order.getCreatedAt() == null) order.setCreatedAt(new Date());
        if (order.getUpdatedAt() == null) order.setUpdatedAt(new Date());
        if (order.getStatus() == null) order.setStatus(EOrderStatus.PENDING);
        
        // Generate order number if not present
        if (order.getOrderNumber() == null) {
            order.setOrderNumber("ORD-" + System.currentTimeMillis() + "-" + 
                (order.getBuyerId() != null ? order.getBuyerId().substring(0, Math.min(4, order.getBuyerId().length())).toUpperCase() : "GUEST"));
        }

        // 1. Try to reduce stock for all items first (Real-Time Deduction)
        List<OrderItem> reducedItems = new ArrayList<>();
        if (order.getItems() != null) {
            try {
                for (OrderItem item : order.getItems()) {
                    reduceStockInServices(order.getSellerId(), item.getProductId(), item.getQuantity());
                    reducedItems.add(item);
                }
            } catch (Exception e) {
                // 2. Compensation: Rollback stock reduction if any fails
                for (OrderItem reducedItem : reducedItems) {
                    restockInServices(order.getSellerId(), reducedItem.getProductId(), reducedItem.getQuantity());
                }
                throw new RuntimeException(e.getMessage());
            }
        }
        
        // 3. Save order only if stock reduction succeeded
        Order savedOrder = orderRepository.save(order);
        return convertToDto(savedOrder);
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
                throw new RuntimeException("Order is locked and cannot be edited in its current status: " + currentStatus);
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
