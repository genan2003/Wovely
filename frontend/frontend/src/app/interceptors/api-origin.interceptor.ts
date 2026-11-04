import { HttpInterceptorFn } from '@angular/common/http';
import { PLATFORM_ID, inject } from '@angular/core';
import { isPlatformServer } from '@angular/common';

/** Where server-side rendering reaches the gateway (env API_ORIGIN); browsers use the page origin. */
export const SSR_API_ORIGIN =
  (globalThis as { process?: { env?: Record<string, string | undefined> } }).process?.env?.['API_ORIGIN']
  ?? 'http://localhost:8090';

/**
 * Relative `/api/...` URLs work in the browser (same origin via nginx / dev proxy) but not during SSR,
 * which has no page origin: there, prefix the gateway origin (PLAN.md 4.1).
 */
export const apiOriginInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.url.startsWith('/') && isPlatformServer(inject(PLATFORM_ID))) {
    return next(req.clone({ url: SSR_API_ORIGIN + req.url }));
  }
  return next(req);
};
