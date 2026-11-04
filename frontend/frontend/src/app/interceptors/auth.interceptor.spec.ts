import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { authInterceptor } from './auth.interceptor';
import { StorageService } from '../services/storage.service';

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
    storage.saveUser({ id: 'b-1', username: 'testuser', roles: ['ROLE_USER'] });
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
    expect(storage.getUser().refreshToken).toBeUndefined();
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
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
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

  it('passes through non-401 errors untouched', () => {
    let error: any;
    http.get(API).subscribe({ error: (e) => (error = e) });
    backend.expectOne(API).flush(null, { status: 403, statusText: 'Forbidden' });
    backend.expectNone(REFRESH);
    expect(error.status).toBe(403);
  });
});
