import { Injectable, signal, inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

const API_URL = 'http://localhost:8081/api/returns';

export interface ReturnRequest {
    id?: string;
    orderId: string;
    orderNumber: string;
    buyerId: string;
    sellerId: string;
    sellerName: string;
    items: any[];
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
    private platformId = inject(PLATFORM_ID);
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

    public updateStatus(id: string, status: string): Observable<any> {
        return this.http.patch(`${API_URL}/${id}/status`, { status });
    }
}
