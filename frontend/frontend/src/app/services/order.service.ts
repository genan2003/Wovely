import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateOrderRequest, Order, UpdateOrderRequest } from '../models/order.model';
import { environment } from '../../environments/environment';

const API_URL = environment.apiUrl + '/orders';
const SELLER_API = environment.apiUrl + '/seller';

@Injectable({
    providedIn: 'root'
})
export class OrderService {
    private http = inject(HttpClient);

    /**
     * Create a new order (Checkout).
     * This triggers real-time stock deduction.
     */
    createOrder(order: CreateOrderRequest): Observable<Order> {
        return this.http.post<Order>(API_URL, order);
    }

    /**
     * Get orders for a specific buyer.
     */
    getOrdersByBuyer(buyerId: string): Observable<Order[]> {
        return this.http.get<Order[]>(`${API_URL}/buyer/${buyerId}`);
    }

    /**
     * Get a specific order by ID.
     */
    getOrderById(id: string): Observable<Order> {
        return this.http.get<Order>(`${API_URL}/${id}`);
    }

    /**
     * Confirm delivery of an order.
     */
    confirmReceipt(orderId: string): Observable<Order> {
        return this.http.post<Order>(`${API_URL}/${orderId}/confirm-receipt`, {});
    }

    /**
     * Mark items as reviewed.
     */
    markReviewed(orderId: string, productIds: string[]): Observable<Order> {
        return this.http.post<Order>(`${API_URL}/${orderId}/mark-reviewed`, productIds);
    }

    /**
     * Cancel an order.
     */
    cancelOrder(orderId: string, reason: string): Observable<Order> {
        return this.http.patch<Order>(`${SELLER_API}/orders/${orderId}/status`, { status: 'CANCELLED', reason });
    }

    /**
     * Update order details (variations or shipping address) during pre-processing.
     * Locked once the order moves beyond PROCESSING or gets a shipping label (409).
     */
    updateOrder(orderId: string, orderData: UpdateOrderRequest): Observable<Order> {
        return this.http.put<Order>(`${SELLER_API}/orders/${orderId}/edit`, orderData);
    }
}
