package com.wovely.wovely.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wovely.wovely.models.EOrderStatus;
import com.wovely.wovely.payload.request.OrderInterventionRequest;
import com.wovely.wovely.payload.response.OrderDTO;
import com.wovely.wovely.services.OrderService;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class OrderAdminController {

    @Autowired
    OrderService orderService;

    @GetMapping
    public ResponseEntity<List<OrderDTO>> getAllOrders() {
        List<OrderDTO> orders = orderService.getAllOrders();
        return new ResponseEntity<>(orders, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDTO> getOrderById(@PathVariable("id") String id) {
        OrderDTO order = orderService.getOrderById(id);
        if (order != null) {
            return new ResponseEntity<>(order, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<OrderDTO> getOrderByOrderNumber(@PathVariable("orderNumber") String orderNumber) {
        OrderDTO order = orderService.getOrderByOrderNumber(orderNumber);
        if (order != null) {
            return new ResponseEntity<>(order, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<OrderDTO>> searchOrders(@RequestParam String query) {
        List<OrderDTO> orders = orderService.searchOrders(query);
        return new ResponseEntity<>(orders, HttpStatus.OK);
    }

    @GetMapping("/buyer/{buyerId}")
    public ResponseEntity<List<OrderDTO>> getOrdersByBuyerId(@PathVariable("buyerId") String buyerId) {
        List<OrderDTO> orders = orderService.getOrdersByBuyerId(buyerId);
        return new ResponseEntity<>(orders, HttpStatus.OK);
    }

    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<List<OrderDTO>> getOrdersBySellerId(@PathVariable("sellerId") String sellerId) {
        List<OrderDTO> orders = orderService.getOrdersBySellerId(sellerId);
        return new ResponseEntity<>(orders, HttpStatus.OK);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<OrderDTO>> getOrdersByStatus(@PathVariable("status") EOrderStatus status) {
        List<OrderDTO> orders = orderService.getOrdersByStatus(status);
        return new ResponseEntity<>(orders, HttpStatus.OK);
    }

    @GetMapping("/disputed")
    public ResponseEntity<List<OrderDTO>> getDisputedOrders() {
        List<OrderDTO> orders = orderService.getDisputedOrders();
        return new ResponseEntity<>(orders, HttpStatus.OK);
    }

    @PostMapping("/{id}/intervene")
    public ResponseEntity<OrderDTO> applyIntervention(
            @PathVariable("id") String id,
            @RequestBody OrderInterventionRequest request) {
        OrderDTO order = orderService.applyIntervention(id, request);
        if (order != null) {
            return new ResponseEntity<>(order, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping("/{id}/regenerate-label")
    public ResponseEntity<OrderDTO> regenerateShippingLabel(@PathVariable("id") String id) {
        OrderDTO order = orderService.regenerateShippingLabel(id);
        if (order != null) {
            return new ResponseEntity<>(order, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}
