package com.wovely.wovely.support;

import java.util.Date;

import com.wovely.wovely.models.EOrderStatus;
import com.wovely.wovely.models.Order;
import com.wovely.wovely.models.OrderItem;

/** Test data builders for orders. */
public final class Orders {

    private Orders() {
    }

    public static Order order(String id, String buyerId, String sellerId, EOrderStatus status) {
        Order o = new Order();
        o.setId(id);
        o.setOrderNumber("ORD-" + id);
        o.setBuyerId(buyerId);
        o.setBuyerName("testuser");
        o.setSellerId(sellerId);
        o.setSellerName("seller");
        o.setItems(new OrderItem[] { new OrderItem("p-1", "Basket", 2, 10.0, "img") });
        o.setTotalAmount(20.0);
        o.setStatus(status);
        o.setCreatedAt(new Date());
        o.setUpdatedAt(new Date());
        return o;
    }
}
