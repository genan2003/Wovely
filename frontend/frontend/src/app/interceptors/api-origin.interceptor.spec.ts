import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PLATFORM_ID } from '@angular/core';
import { SSR_API_ORIGIN, apiOriginInterceptor } from './api-origin.interceptor';

describe('apiOriginInterceptor', () => {
  function setup(platform: 'browser' | 'server') {
    TestBed.configureTestingModule({
      providers: [
        { provide: PLATFORM_ID, useValue: platform },
        provideHttpClient(withInterceptors([apiOriginInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    return { http: TestBed.inject(HttpClient), backend: TestBed.inject(HttpTestingController) };
  }

  it('keeps relative URLs in the browser (same origin through the gateway)', () => {
    const { http, backend } = setup('browser');
    http.get('/api/products').subscribe();
    backend.expectOne('/api/products').flush([]);
    backend.verify();
  });

  it('prefixes the gateway origin during server-side rendering', () => {
    const { http, backend } = setup('server');
    http.get('/api/products').subscribe();
    backend.expectOne(`${SSR_API_ORIGIN}/api/products`).flush([]);
    http.get('https://example.test/x').subscribe();
    backend.expectOne('https://example.test/x').flush({});
    backend.verify();
  });
});
