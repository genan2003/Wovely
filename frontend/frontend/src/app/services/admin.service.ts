import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Product } from '../models/product.model';
import { Order, OrderInterventionRequest } from '../models/order.model';

const API_URL = 'http://localhost:8082/api/products/admin';

const AUTH_API_URL = 'http://localhost:8081/api/admin/users';

const ORDER_API_URL = 'http://localhost:8081/api/admin/orders';

@Injectable({
  providedIn: 'root'
})
export class AdminService {

  constructor(private http: HttpClient) { }

  getPendingAds(): Observable<Product[]> {
    return this.http.get<Product[]>(API_URL + '/pending');
  }

  updateAdStatus(id: string, status: string): Observable<Product> {
    return this.http.put<Product>(API_URL + `/${id}/status`, { status });
  }

  updateAdContent(id: string, product: Product): Observable<Product> {
    return this.http.put<Product>(API_URL + `/${id}`, product);
  }

  // User CRM API

  getAllUsers(): Observable<any[]> {
    return this.http.get<any[]>(AUTH_API_URL);
  }

  getUserById(id: string): Observable<any> {
    return this.http.get<any>(AUTH_API_URL + `/${id}`);
  }

  applyPenalty(id: string, payload: any): Observable<any> {
    return this.http.post<any>(AUTH_API_URL + `/${id}/penalty`, payload);
  }

  getSellerProducts(sellerId: string): Observable<Product[]> {
    return this.http.get<Product[]>(API_URL + `/seller/${sellerId}`);
  }

  // Order Resolution API

  getAllOrders(): Observable<Order[]> {
    return this.http.get<Order[]>(ORDER_API_URL);
  }

  getOrderById(id: string): Observable<Order> {
    return this.http.get<Order>(ORDER_API_URL + `/${id}`);
  }

  getOrderByOrderNumber(orderNumber: string): Observable<Order> {
    return this.http.get<Order>(ORDER_API_URL + `/number/${orderNumber}`);
  }

  searchOrders(query: string): Observable<Order[]> {
    return this.http.get<Order[]>(ORDER_API_URL + `/search?query=${query}`);
  }

  getOrdersByBuyerId(buyerId: string): Observable<Order[]> {
    return this.http.get<Order[]>(ORDER_API_URL + `/buyer/${buyerId}`);
  }

  getOrdersBySellerId(sellerId: string): Observable<Order[]> {
    return this.http.get<Order[]>(ORDER_API_URL + `/seller/${sellerId}`);
  }

  getOrdersByStatus(status: string): Observable<Order[]> {
    return this.http.get<Order[]>(ORDER_API_URL + `/status/${status}`);
  }

  getDisputedOrders(): Observable<Order[]> {
    return this.http.get<Order[]>(ORDER_API_URL + '/disputed');
  }

  applyIntervention(orderId: string, request: OrderInterventionRequest): Observable<Order> {
    return this.http.post<Order>(ORDER_API_URL + `/${orderId}/intervene`, request);
  }

  regenerateShippingLabel(orderId: string): Observable<Order> {
    return this.http.post<Order>(ORDER_API_URL + `/${orderId}/regenerate-label`, {});
  }
}
