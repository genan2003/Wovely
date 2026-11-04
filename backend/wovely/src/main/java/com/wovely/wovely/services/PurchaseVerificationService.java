package com.wovely.wovely.services;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.wovely.wovely.models.EOrderStatus;
import com.wovely.wovely.repository.OrderRepository;

/**
 * "Verified Purchase" rule (PLAN.md 3.3, README buyer story): a buyer has actually received the goods,
 * i.e. owns an order in {@link #RECEIVED} that contains the product (or comes from the seller).
 * Cancelled, refunded or still-in-transit orders don't count.
 */
@Service
public class PurchaseVerificationService {

    public static final Set<EOrderStatus> RECEIVED = EnumSet.of(EOrderStatus.DELIVERED, EOrderStatus.COMPLETED);

    private final OrderRepository orderRepository;

    public PurchaseVerificationService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public boolean hasReceivedProduct(String buyerId, String productId) {
        if (isBlank(buyerId) || isBlank(productId)) {
            return false;
        }
        return orderRepository.existsByBuyerIdAndStatusInAndItemsProductId(buyerId, RECEIVED, productId);
    }

    public boolean hasReceivedFromSeller(String buyerId, String sellerId) {
        if (isBlank(buyerId) || isBlank(sellerId)) {
            return false;
        }
        return orderRepository.existsByBuyerIdAndSellerIdAndStatusIn(buyerId, sellerId, RECEIVED);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
