import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Product } from '../models/product.model';

const API_URL = 'http://localhost:8082/api/products/admin';

const AUTH_API_URL = 'http://localhost:8081/api/admin/users';

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
}
