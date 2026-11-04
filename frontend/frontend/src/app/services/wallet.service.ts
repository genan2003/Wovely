import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { WalletBalance, WalletTransaction } from '../models/wallet.model';
import { environment } from '../../environments/environment';

const API_URL = environment.apiUrl + '/wallet';

/** The signed-in user's demo wallet (PLAN.md 3.7). Only an amount is ever sent — never card data. */
@Injectable({ providedIn: 'root' })
export class WalletService {
  private http = inject(HttpClient);

  /** Last known balance in cents, shared by Settings and the cart. */
  readonly balanceCents = signal<number | null>(null);

  refresh(): Observable<WalletBalance> {
    return this.http.get<WalletBalance>(API_URL).pipe(tap((b) => this.balanceCents.set(b.balanceCents)));
  }

  topUp(amount: number): Observable<WalletBalance> {
    return this.http.post<WalletBalance>(`${API_URL}/top-up`, { amount })
      .pipe(tap((b) => this.balanceCents.set(b.balanceCents)));
  }

  transactions(limit = 20): Observable<WalletTransaction[]> {
    return this.http.get<WalletTransaction[]>(`${API_URL}/transactions`, { params: { limit } });
  }
}
