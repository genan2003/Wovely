package com.wovely.wovely.services;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    private static final String PRODUCTS_API = "http://localhost:8082/api/products";

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
                    order.setStatus(request.getNewStatus());
                    
                    if (EOrderStatus.CANCELLED.equals(request.getNewStatus())) {
                        order.setCancellationReason(request.getReason());
                    } else if (EOrderStatus.REFUNDED.equals(request.getNewStatus())) {
                        order.setRefundReason(request.getReason());
                    }
                }
            } else if ("FORCE_REFUND".equals(action)) {
                order.setStatus(EOrderStatus.REFUNDED);
                order.setRefundReason(request.getReason());
            } else if ("CANCEL_ORDER".equals(action)) {
                order.setStatus(EOrderStatus.CANCELLED);
                order.setCancellationReason(request.getReason());
            } else if ("UPDATE_TRACKING".equals(action)) {
                order.setTrackingNumber(request.getTrackingNumber());
            } else if ("MARK_DISPUTED".equals(action)) {
                order.setIsDisputed(true);
                order.setDisputeReason(request.getReason());
                order.setStatus(EOrderStatus.DISPUTED);
            } else if ("RESOLVE_DISPUTE".equals(action)) {
                order.setIsDisputed(false);
                order.setDisputeReason(null);
                if (request.getNewStatus() != null) {
                    order.setStatus(request.getNewStatus());
                }
            }
            
            if (request.getAdminNotes() != null && !request.getAdminNotes().isEmpty()) {
                order.setAdminNotes(request.getAdminNotes());
            }
            
            order.setUpdatedAt(new Date());
            orderRepository.save(order);
            
            return convertToDto(order);
        }
        
        return null;
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

        order = orderRepository.save(order);

        // Reduce stock for each item in the products service
        for (com.wovely.wovely.models.ManualOrderItemRequest orderItem : request.getItems()) {
            try {
                String stockUrl = PRODUCTS_API + "/seller/" + request.getSellerId() + "/product/" +
                    orderItem.getProductId() + "/stock";

                // Get current product to calculate new stock
                String productUrl = PRODUCTS_API + "/seller/" + request.getSellerId() + "/product/" +
                    orderItem.getProductId();

                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> currentProduct = restTemplate.getForObject(productUrl,
                    java.util.Map.class);

                if (currentProduct != null) {
                    int currentStock = ((Number) currentProduct.get("stockQuantity")).intValue();
                    int newStock = Math.max(0, currentStock - orderItem.getQuantity());

                    restTemplate.put(stockUrl, java.util.Map.of("quantity", newStock));
                }
            } catch (Exception e) {
                // Log but don't fail the order if stock update fails
                System.err.println("Failed to update stock for product: " + orderItem.getProductId());
            }
        }

        return convertToDto(order);
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
            order.getDisputeReason()
        );
    }
}
