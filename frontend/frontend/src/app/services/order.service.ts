import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Order } from '../models/order.model';

const API_URL = 'http://localhost:8081/api/orders';
const SELLER_API = 'http://localhost:8081/api/seller';

@Injectable({
    providedIn: 'root'
})
export class OrderService {
    private http = inject(HttpClient);

    constructor() { }

    /**
     * Create a new order (Checkout).
     * This triggers real-time stock deduction.
     */
    createOrder(order: any): Observable<any> {
        return this.http.post<any>(API_URL, order);
    }

    /**
     * Get orders for a specific buyer.
     */
    getOrdersByBuyer(buyerId: string): Observable<any[]> {
        return this.http.get<any[]>(`${API_URL}/buyer/${buyerId}`);
    }

    /**
     * Get a specific order by ID.
     */
    getOrderById(id: string): Observable<any> {
        return this.http.get<any>(`${API_URL}/${id}`);
    }

    /**
     * Update order details (variations or shipping address) during pre-processing.
     * Locked once order is in PROCESSING status or beyond.
     */
    updateOrder(orderId: string, orderData: any): Observable<any> {
        return this.http.put<any>(`${SELLER_API}/orders/${orderId}/edit`, orderData);
    }
}
