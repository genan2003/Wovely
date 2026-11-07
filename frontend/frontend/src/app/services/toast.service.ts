import { Injectable, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';

export type ToastTone = 'success' | 'error' | 'info';

export interface Toast {
  id: number;
  tone: ToastTone;
  message: string;
}

const LIFETIME_MS: Record<ToastTone, number> = { success: 4000, info: 5000, error: 8000 };

/** The message the API returned, or `fallback` when it has none we can show. */
export function apiErrorMessage(err: unknown, fallback: string): string {
  const message = err instanceof HttpErrorResponse ? err.error?.message : undefined;
  return typeof message === 'string' && message.trim() ? message : fallback;
}

/** In-app notifications, rendered once by ToastOutletComponent. Replaces `alert()` (PLAN.md 5.3). */
@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly toasts = signal<Toast[]>([]);

  private nextId = 1;
  /** Errors the auth interceptor already announced, so a component's handler doesn't repeat them. */
  private announced = new WeakSet<object>();

  success(message: string): void {
    this.show('success', message);
  }

  info(message: string): void {
    this.show('info', message);
  }

  error(message: string): void {
    this.show('error', message);
  }

  /** Reports a failed request, preferring the server's message. */
  apiError(err: unknown, fallback: string): void {
    if (typeof err === 'object' && err !== null && this.announced.has(err)) {
      return;
    }
    this.error(apiErrorMessage(err, fallback));
  }

  /** For interceptors: show the error once and silence later `apiError` calls for it. */
  announce(err: HttpErrorResponse, fallback: string): void {
    this.announced.add(err);
    this.error(apiErrorMessage(err, fallback));
  }

  dismiss(id: number): void {
    this.toasts.update((list) => list.filter((toast) => toast.id !== id));
  }

  private show(tone: ToastTone, message: string): void {
    if (this.toasts().some((toast) => toast.tone === tone && toast.message === message)) {
      return;
    }
    const id = this.nextId++;
    this.toasts.update((list) => [...list, { id, tone, message }]);
    setTimeout(() => this.dismiss(id), LIFETIME_MS[tone]);
  }
}
