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

@Service
public class OrderService {

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    EcoCarrierService ecoCarrierService;

    private static final String PRODUCTS_API = "http://localhost:8082/api/products";
    private static final String INVENTORY_API = "http://localhost:8083/api/inventory";

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

    public List<OrderDTO> searchOrders(String query) {
        List<Order> orders = orderRepository.findByBuyerNameContainingIgnoreCaseOrSellerNameContainingIgnoreCaseOrOrderNumberContaining(
            query, query, query);
        return orders.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    public List<OrderDTO> getOrdersByBuyerId(String buyerId) {
        List<Order> orders = orderRepository.findByBuyerId(buyerId);
        return orders.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    public List<OrderDTO> getOrdersBySellerId(String sellerId) {
        List<Order> orders = orderRepository.findBySellerId(sellerId);
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

    public OrderDTO applyIntervention(String orderId, OrderInterventionRequest request) {
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
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            EOrderStatus oldStatus = order.getStatus();
            
            if (oldStatus == newStatus) {
                return convertToDto(order);
            }

            order.setStatus(newStatus);
            
            if (EOrderStatus.CANCELLED.equals(newStatus)) {
                order.setCancellationReason(reason);
            } else if (EOrderStatus.REFUNDED.equals(newStatus)) {
                order.setRefundReason(reason);
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
     * Returns true if successful, false if not enough stock or other error.
     */
    private boolean reduceStockInServices(String sellerId, String productId, int quantity) {
        try {
            String productsUrl = PRODUCTS_API + "/seller/" + sellerId + "/product/" + productId + "/reduce-stock";
            String inventoryUrl = INVENTORY_API + "/seller/" + sellerId + "/product/" + productId + "/reduce-stock";

            // Try reducing in products service first
            restTemplate.postForEntity(productsUrl, java.util.Map.of("quantity", quantity), Object.class);
            
            // Then try in inventory service (best effort sync)
            try {
                restTemplate.postForEntity(inventoryUrl, java.util.Map.of("quantity", quantity), Object.class);
            } catch (Exception e) {
                System.err.println("Inventory service sync failed for product " + productId + ": " + e.getMessage());
            }
            
            return true;
        } catch (Exception e) {
            System.err.println("Failed to reduce stock in products service for product " + productId + ": " + e.getMessage());
            return false;
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

    public OrderDTO regenerateShippingLabel(String orderId) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);

        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            String newTrackingNumber = "TRK-" + System.currentTimeMillis() + "-" + orderId.substring(0, 6).toUpperCase();
            order.setTrackingNumber(newTrackingNumber);
            order.setUpdatedAt(new Date());
            orderRepository.save(order);
            return convertToDto(order);
        }

        return null;
    }

    /**
     * Automated Eco-Shipping: Best Low-CO2 Carrier selection and label generation.
     */
    public OrderDTO generateEcoShippingLabel(String orderId) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);

        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            
            // 1. Automatically select best eco-carrier
            EcoCarrierService.EcoCarrier carrier = ecoCarrierService.selectBestCarrier(order.getShippingAddress());
            
            // 2. Generate prepaid label
            String label = ecoCarrierService.generateLabel(order.getOrderNumber(), carrier, order.getShippingAddress());
            
            // 3. Update order with label and mock tracking
            order.setEcoShippingLabel(label);
            if (order.getTrackingNumber() == null) {
                order.setTrackingNumber("ECO-" + System.currentTimeMillis() + "-" + carrier.getName().substring(0, 3).toUpperCase());
            }
            
            order.setUpdatedAt(new Date());
            Order savedOrder = orderRepository.save(order);
            return convertToDto(savedOrder);
        }

        return null;
    }

    /**
     * Create a manual order for off-platform sales (e.g., craft fair sales).
     * This reduces inventory stock and creates an order record.
     */
    public OrderDTO createManualOrder(ManualOrderRequest request) {
        Order order = new Order();
        
        // Generate unique order number
        String orderNumber = "MAN-" + System.currentTimeMillis() + "-" + 
            request.getSellerId().substring(0, Math.min(4, request.getSellerId().length())).toUpperCase();
        
        order.setOrderNumber(orderNumber);
        order.setBuyerId("OFF_PLATFORM");
        order.setBuyerName(request.getBuyerName());
        order.setSellerId(request.getSellerId());
        order.setSellerName(request.getSellerName());
        order.setShippingAddress(request.getShippingAddress());
        
        // Convert items
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
                if (reduceStockInServices(order.getSellerId(), item.getProductId(), item.getQuantity())) {
                    reducedItems.add(item);
                } else {
                    throw new RuntimeException("Insufficient stock for product: " + item.getProductName());
                }
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
                    if (reduceStockInServices(order.getSellerId(), item.getProductId(), item.getQuantity())) {
                        reducedItems.add(item);
                    } else {
                        throw new RuntimeException("Insufficient stock for product: " + item.getProductName());
                    }
                }
            } catch (Exception e) {
                // 2. Compensation: Rollback stock reduction if any fails
                for (OrderItem reducedItem : reducedItems) {
                    restockInServices(order.getSellerId(), reducedItem.getProductId(), reducedItem.getQuantity());
                }
                throw e;
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
            
            // Update shipping address if provided
            if (updatedOrderData.getShippingAddress() != null) {
                order.setShippingAddress(updatedOrderData.getShippingAddress());
            }
            
            // Update items (variations like color/size)
            if (updatedOrderData.getItems() != null) {
                // Ensure we don't change product IDs or quantities easily without re-checking stock,
                // but for this feature we mainly focus on variations.
                order.setItems(updatedOrderData.getItems());
            }
            
            order.setUpdatedAt(new Date());
            Order savedOrder = orderRepository.save(order);
            return convertToDto(savedOrder);
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
                    item.getImageUrl()
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
            order.getEcoShippingLabel()
        );
    }
}
