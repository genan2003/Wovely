import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { DeliveryOption } from '../models/delivery-option';

/** The server's delivery price list, loaded once. If it can't be loaded, checkout stays disabled. */
@Injectable({ providedIn: 'root' })
export class DeliveryOptionService {
  private http = inject(HttpClient);

  readonly options = signal<DeliveryOption[]>([]);
  readonly failed = signal(false);
  private requested = false;

  load(): void {
    if (this.requested && !this.failed()) return;
    this.requested = true;
    this.failed.set(false);
    this.http.get<DeliveryOption[]>(`${environment.apiUrl}/public/delivery-options`).subscribe({
      next: (options) => this.options.set(options ?? []),
      error: () => this.failed.set(true),
    });
  }

  /** The lowest-CO₂ option is preselected (Wovely's eco default). */
  defaultOption(): DeliveryOption | undefined {
    return [...this.options()].sort((a, b) => a.co2Kg - b.co2Kg)[0];
  }
}
