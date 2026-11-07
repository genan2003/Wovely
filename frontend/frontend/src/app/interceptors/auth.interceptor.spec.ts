import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { authInterceptor } from './auth.interceptor';
import { StorageService } from '../services/storage.service';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

const API = '/api/orders/buyer/b-1';
const REFRESH = '/api/auth/refresh';

describe('authInterceptor (PLAN.md 2.9)', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let storage: StorageService;
  let router: Router;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    storage = TestBed.inject(StorageService);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    storage.saveToken('old-access');
    storage.saveRefreshToken('refresh-1');
    storage.saveUser({ id: 'b-1', username: 'testuser', email: 't@w.ro', roles: ['ROLE_USER'], accessToken: 'old-access' });
    TestBed.inject(AuthService).user.set(storage.getUser());
  });

  afterEach(() => backend.verify());

  it('adds the bearer token', () => {
    http.get(API).subscribe();
    const req = backend.expectOne(API);
    expect(req.request.headers.get('Authorization')).toBe('Bearer old-access');
    req.flush([]);
  });

  it('refreshes once on 401 and retries with the new token', () => {
    let result: unknown;
    http.get(API).subscribe((r) => (result = r));

    backend.expectOne(API).flush(null, { status: 401, statusText: 'Unauthorized' });
    const refresh = backend.expectOne(REFRESH);
    expect(refresh.request.body).toEqual({ refreshToken: 'refresh-1' });
    refresh.flush({ accessToken: 'new-access', refreshToken: 'refresh-2', id: 'b-1', username: 'testuser', roles: [] });

    const retry = backend.expectOne(API);
    expect(retry.request.headers.get('Authorization')).toBe('Bearer new-access');
    retry.flush(['ok']);

    expect(result).toEqual(['ok']);
    expect(storage.getToken()).toBe('new-access');
    expect(storage.getRefreshToken()).toBe('refresh-2');
    expect(storage.getUser()).not.toHaveProperty('refreshToken');
  });

  it('shares one refresh between concurrent 401s', () => {
    http.get(API).subscribe();
    http.get(API + '?again').subscribe();
    backend.match((r) => r.url.startsWith(API)).forEach((r) => r.flush(null, { status: 401, statusText: 'Unauthorized' }));

    const refreshes = backend.match(REFRESH);
    expect(refreshes.length).toBe(1);
    refreshes[0].flush({ accessToken: 'new-access', refreshToken: 'refresh-2' });

    backend.match((r) => r.url.startsWith(API)).forEach((r) => {
      expect(r.request.headers.get('Authorization')).toBe('Bearer new-access');
      r.flush([]);
    });
  });

  it('signs out and redirects to /login when the refresh fails', () => {
    let error: any;
    http.get(API).subscribe({ error: (e) => (error = e) });
    backend.expectOne(API).flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne(REFRESH).flush({ message: 'Invalid refresh token' }, { status: 401, statusText: 'Unauthorized' });

    expect(error.status).toBe(401);
    expect(storage.getToken()).toBeNull();
    expect(storage.getRefreshToken()).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/' } });
  });

  it('does not try to refresh without a refresh token or for auth calls', () => {
    sessionStorage.removeItem('auth-refresh');
    http.get(API).subscribe({ error: () => undefined });
    backend.expectOne(API).flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectNone(REFRESH);

    storage.saveRefreshToken('refresh-1');
    http.post('/api/auth/signin', {}).subscribe({ error: () => undefined });
    backend.expectOne('/api/auth/signin').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectNone(REFRESH);
  });

  it('ends the session on a 401 when there is no refresh token to try (PLAN.md 5.1)', () => {
    sessionStorage.removeItem('auth-refresh');
    http.get(API).subscribe({ error: () => undefined });
    backend.expectOne(API).flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(storage.getToken()).toBeNull();
    expect(TestBed.inject(AuthService).isLoggedIn()).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/' } });
    expect(TestBed.inject(ToastService).toasts().map((t) => t.message)).toEqual([
      'Your session has expired. Log in to continue.',
    ]);
  });

  it('leaves anonymous 401s alone: there is no session to end', () => {
    sessionStorage.clear();
    http.get(API).subscribe({ error: () => undefined });
    backend.expectOne(API).flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(router.navigate).not.toHaveBeenCalled();
    expect(TestBed.inject(ToastService).toasts()).toEqual([]);
  });

  it('announces a 403 with the server message and passes the error on (PLAN.md 5.1)', () => {
    let error: unknown;
    http.get(API).subscribe({ error: (e) => (error = e) });
    backend.expectOne(API).flush({ message: 'Not your order' }, { status: 403, statusText: 'Forbidden' });
    backend.expectNone(REFRESH);

    const toast = TestBed.inject(ToastService);
    expect(toast.toasts().map((t) => t.message)).toEqual(['Not your order']);
    expect((error as HttpErrorResponse).status).toBe(403);

    // A component handling the same failure must not show a second notification
    toast.apiError(error, 'fallback');
    expect(toast.toasts().length).toBe(1);
  });

  it('passes other errors through without a notification', () => {
    let error: unknown;
    http.get(API).subscribe({ error: (e) => (error = e) });
    backend.expectOne(API).flush(null, { status: 500, statusText: 'Server Error' });

    expect((error as HttpErrorResponse).status).toBe(500);
    expect(TestBed.inject(ToastService).toasts()).toEqual([]);
  });
});
