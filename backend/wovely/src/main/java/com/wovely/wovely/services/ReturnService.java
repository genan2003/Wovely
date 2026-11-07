package com.wovely.wovely.services;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.wovely.wovely.clients.CatalogClient;
import com.wovely.common.error.ApiException;
import com.wovely.wovely.models.EOrderStatus;
import com.wovely.wovely.models.EReturnStatus;
import com.wovely.wovely.models.Order;
import com.wovely.wovely.models.OrderItem;
import com.wovely.wovely.models.ReturnRequest;
import com.wovely.wovely.repository.OrderRepository;
import com.wovely.wovely.repository.ReturnRequestRepository;
import com.wovely.wovely.security.OwnershipGuard;
import com.wovely.wovely.services.OrderStatusPolicy.Actor;

/** Return requests: creation by the order's buyer and the status lifecycle with restock/refund. */
@Service
public class ReturnService {

    private final ReturnRequestRepository returnRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final CatalogClient catalogClient;
    private final OwnershipGuard ownership;
    private final AuditService auditService;
    private final WalletService walletService;
    private final com.wovely.wovely.clients.InventoryClient inventoryClient;

    public ReturnService(ReturnRequestRepository returnRepository, OrderRepository orderRepository,
            OrderService orderService, CatalogClient catalogClient, OwnershipGuard ownership, AuditService auditService,
            WalletService walletService, com.wovely.wovely.clients.InventoryClient inventoryClient) {
        this.walletService = walletService;
        this.inventoryClient = inventoryClient;
        this.returnRepository = returnRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.catalogClient = catalogClient;
        this.ownership = ownership;
        this.auditService = auditService;
    }

    /** Buyer opens a return on a delivered/completed order; ids, prices and totals come from the order. */
    public ReturnRequest create(com.wovely.wovely.payload.request.CreateReturnRequest request) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> ApiException.badRequest("Original order not found."));
        ownership.requireAnyOf(order.getBuyerId());
        if (order.getStatus() != EOrderStatus.DELIVERED && order.getStatus() != EOrderStatus.COMPLETED) {
            throw ApiException.conflict("Only delivered orders can be returned (current status: " + order.getStatus() + ")");
        }
        Date deadline = order.getReturnDeadline();
        if (deadline == null || new Date().after(deadline)) {
            throw ApiException.conflict("The " + Order.RETURN_WINDOW_DAYS
                    + "-day return window for this order has closed");
        }

        List<String> requested = request.productIds();
        List<OrderItem> returned = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            if (requested.contains(item.getProductId())) {
                if (item.isReturned()) {
                    throw ApiException.badRequest("Return already requested for item: " + item.getProductName());
                }
                item.setReturned(true);
                returned.add(item);
            }
        }
        if (returned.isEmpty()) {
            throw ApiException.badRequest("No valid items selected for return.");
        }

        ReturnRequest toSave = new ReturnRequest();
        toSave.setOrderId(order.getId());
        toSave.setOrderNumber(order.getOrderNumber());
        toSave.setBuyerId(order.getBuyerId());
        toSave.setSellerId(order.getSellerId());
        toSave.setSellerName(order.getSellerName());
        toSave.setItems(returned);
        toSave.setReason(request.reason());
        toSave.setStatus(EReturnStatus.PICKED_UP.name());
        toSave.setTotalCents(returned.stream().mapToLong(OrderItem::lineTotalCents).reduce(0L, Math::addExact));

        orderRepository.save(order);
        ReturnRequest saved = returnRepository.save(toSave);
        auditService.record("RETURN_REQUESTED", saved.getId(), "Order " + order.getOrderNumber() + ", amount " + saved.getTotalAmount());
        return saved;
    }

    /**
     * Seller of the order (or admin) advances the return. Illegal transitions → 409.
     * REFUNDED: returned items are restocked and the amount is added to the order's refundedAmount;
     * once everything is refunded the order itself becomes REFUNDED.
     */
    public ReturnRequest updateStatus(String returnId, String statusValue) {
        ReturnRequest ret = returnRepository.findById(returnId)
                .orElseThrow(() -> ApiException.notFound("Return request not found"));
        ownership.requireAnyOf(ret.getSellerId());

        EReturnStatus next;
        try {
            next = EReturnStatus.valueOf(statusValue == null ? "" : statusValue);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("status must be one of " + java.util.Arrays.toString(EReturnStatus.values()));
        }
        EReturnStatus current = EReturnStatus.fromStored(ret.getStatus());
        if (!current.canMoveTo(next)) {
            throw ApiException.conflict("Cannot change return status from " + current + " to " + next);
        }

        if (next == EReturnStatus.REFUNDED) {
            refund(ret);
        }
        ret.setStatus(next.name());
        ReturnRequest saved = returnRepository.save(ret);
        auditService.record("RETURN_STATUS_UPDATE", returnId, current + " -> " + next);
        return saved;
    }

    private void refund(ReturnRequest ret) {
        for (OrderItem item : ret.getItems()) {
            inventoryClient.release(ret.getSellerId(), item.getProductId(), item.getQuantity());
        }
        orderRepository.findById(ret.getOrderId()).ifPresent(order -> {
            long returnCents = ret.getTotalCents() == null ? 0 : ret.getTotalCents();
            long refunded = Math.addExact(order.getRefundedCents() == null ? 0 : order.getRefundedCents(), returnCents);
            order.setRefundedCents(refunded);
            walletService.refundReturn(order, returnCents);
            // Every item came back: the order becomes REFUNDED, whose transition hook refunds what is left
            // of the payment — the delivery fee (returns themselves refund item prices only).
            if (refunded >= order.itemsTotalCents() && !OrderStatusPolicy.isTerminal(order.getStatus())) {
                // Goods are back and restocked above, so this must not restock again (restocksOn is false
                // for DELIVERED/COMPLETED → REFUNDED).
                orderService.transition(order, EOrderStatus.REFUNDED, Actor.SYSTEM, "All items returned");
            }
            order.setUpdatedAt(new Date());
            orderRepository.save(order);
        });
    }
}
