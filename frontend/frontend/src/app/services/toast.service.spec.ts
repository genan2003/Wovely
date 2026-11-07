import { HttpErrorResponse } from '@angular/common/http';
import { vi } from 'vitest';

import { ToastService, apiErrorMessage } from './toast.service';

describe('ToastService', () => {
  let toast: ToastService;

  beforeEach(() => {
    vi.useFakeTimers();
    toast = new ToastService();
  });
  afterEach(() => vi.useRealTimers());

  it('removes a toast after its lifetime, keeping errors longer', () => {
    toast.success('Saved');
    toast.error('Failed');
    vi.advanceTimersByTime(4000);
    expect(toast.toasts().map((t) => t.message)).toEqual(['Failed']);
    vi.advanceTimersByTime(4000);
    expect(toast.toasts()).toEqual([]);
  });

  it('does not stack the same message twice', () => {
    toast.error('Failed');
    toast.error('Failed');
    expect(toast.toasts().length).toBe(1);
  });

  it('can be dismissed by id', () => {
    toast.info('Hello');
    toast.dismiss(toast.toasts()[0].id);
    expect(toast.toasts()).toEqual([]);
  });

  it('prefers the server message over the fallback', () => {
    const withMessage = new HttpErrorResponse({ status: 409, error: { message: 'Only 2 left' } });
    const withoutMessage = new HttpErrorResponse({ status: 500 });
    expect(apiErrorMessage(withMessage, 'fallback')).toBe('Only 2 left');
    expect(apiErrorMessage(withoutMessage, 'fallback')).toBe('fallback');
    expect(apiErrorMessage(new Error('boom'), 'fallback')).toBe('fallback');
  });
});
