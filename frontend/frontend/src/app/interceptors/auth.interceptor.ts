import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { StorageService } from '../services/storage.service';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

const withToken = (req: HttpRequest<unknown>, token: string | null) =>
  token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

const isAuthCall = (url: string) => url.includes('/api/auth/');

/**
 * Adds the access token and handles auth failures in one place (PLAN.md 2.9, 5.1):
 * - 401: retry once with a refreshed token; if the session can't be renewed, sign out and go to /login.
 * - 403: tell the user, so no screen fails silently.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const storageService = inject(StorageService);
  const authService = inject(AuthService);
  const router = inject(Router);
  const toast = inject(ToastService);

  const endSession = () => {
    authService.clearSession();
    toast.info('Your session has expired. Log in to continue.');
    router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
  };

  const token = storageService.getToken();
  return next(withToken(req, token)).pipe(
    catchError((err: unknown) => {
      if (!(err instanceof HttpErrorResponse) || isAuthCall(req.url)) {
        return throwError(() => err);
      }
      if (err.status === 403) {
        toast.announce(err, 'You don’t have permission to do that.');
        return throwError(() => err);
      }
      if (err.status !== 401 || !token) {
        return throwError(() => err);
      }
      if (!storageService.getRefreshToken()) {
        endSession();
        return throwError(() => err);
      }
      return authService.refreshAccessToken().pipe(
        switchMap((fresh) => next(withToken(req, fresh))),
        catchError((retryErr: unknown) => {
          const refreshFailed =
            retryErr instanceof HttpErrorResponse && !!retryErr.url && isAuthCall(retryErr.url);
          if (refreshFailed) {
            endSession();
            return throwError(() => err);
          }
          return throwError(() => retryErr);
        })
      );
    })
  );
};
