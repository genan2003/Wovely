import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Product } from '../models/product.model';
import { Order, OrderInterventionRequest } from '../models/order.model';
import { AccountStatusFilter, AuditLog, PenaltyRequest, UserCrm } from '../models/user-crm.model';
import { CreateReportRequest, Report } from '../models/report.model';
import { ChatMessage } from '../models/chat.model';
import { environment } from '../../environments/environment';

const API_URL = environment.apiUrl + '/products/admin';

const AUTH_API_URL = environment.apiUrl + '/admin/users';

const ORDER_API_URL = environment.apiUrl + '/admin/orders';

const REPORTS_API_URL = environment.apiUrl + '/reports';

@Injectable({
  providedIn: 'root',
})
export class AdminService {
  private http = inject(HttpClient);

  // Reports API

  createReport(report: CreateReportRequest): Observable<Report> {
    return this.http.post<Report>(REPORTS_API_URL, report);
  }

  getAllReports(): Observable<Report[]> {
    return this.http.get<Report[]>(REPORTS_API_URL + '/all');
  }

  updateReportStatus(id: string, status: 'RESOLVED' | 'DISMISSED'): Observable<Report> {
    return this.http.patch<Report>(REPORTS_API_URL + `/${id}/status`, { status });
  }

  getReportedChatLog(reportId: string): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(REPORTS_API_URL + `/chat-log/${reportId}`);
  }

  // Audit Log API

  getAuditLogs(): Observable<AuditLog[]> {
    return this.http.get<AuditLog[]>(ORDER_API_URL + '/audit');
  }

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

  getAllUsers(): Observable<UserCrm[]> {
    return this.http.get<UserCrm[]>(AUTH_API_URL);
  }

  /** Server-side CRM search (PLAN.md 3.1): case-insensitive on username, email and full name. */
  searchUsers(query: string, status: AccountStatusFilter): Observable<UserCrm[]> {
    let params = new HttpParams();
    if (query.trim()) params = params.set('query', query.trim());
    if (status !== 'ALL') params = params.set('status', status);
    return this.http.get<UserCrm[]>(AUTH_API_URL, { params });
  }

  getUserById(id: string): Observable<UserCrm> {
    return this.http.get<UserCrm>(AUTH_API_URL + `/${id}`);
  }

  applyPenalty(id: string, payload: PenaltyRequest): Observable<UserCrm> {
    return this.http.post<UserCrm>(AUTH_API_URL + `/${id}/penalty`, payload);
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
    return this.http.get<Order[]>(ORDER_API_URL + '/search', { params: { query } });
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
