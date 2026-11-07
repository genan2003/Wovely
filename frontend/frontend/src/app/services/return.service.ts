import { Injectable, signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { OrderItem } from '../models/order.model';

const API_URL = environment.apiUrl + '/returns';

export interface ReturnItem extends OrderItem {
    returnQuantity: number;
}

export interface ReturnRequest {
    id?: string;
    orderId: string;
    orderNumber: string;
    buyerId: string;
    sellerId: string;
    sellerName: string;
    items: ReturnItem[];
    reason: string;
    status: 'PICKED_UP' | 'ARRIVED' | 'INSPECTION' | 'REFUNDED' | 'DENIED';
    totalAmount: number;
    createdAt: Date;
}

@Injectable({
    providedIn: 'root'
})
export class ReturnService {
    private http = inject(HttpClient);
    private returns = signal<ReturnRequest[]>([]);

    public getReturns = this.returns.asReadonly();

    public loadBuyerReturns(buyerId: string): void {
        this.http.get<ReturnRequest[]>(`${API_URL}/buyer/${buyerId}`).subscribe(data => {
            this.returns.set(data);
        });
    }

    public loadSellerReturns(sellerId: string): Observable<ReturnRequest[]> {
        return this.http.get<ReturnRequest[]>(`${API_URL}/seller/${sellerId}`);
    }

    public loadAllReturns(): Observable<ReturnRequest[]> {
        return this.http.get<ReturnRequest[]>(`${API_URL}/all`);
    }

    public createReturn(req: ReturnRequest): Observable<ReturnRequest> {
        return this.http.post<ReturnRequest>(API_URL, req).pipe(
            tap(saved => {
                this.returns.update(prev => [saved, ...prev]);
            })
        );
    }

    public updateStatus(id: string, status: string): Observable<ReturnRequest> {
        return this.http.patch<ReturnRequest>(`${API_URL}/${id}/status`, { status });
    }
}
