import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { StorageService } from '../services/storage.service';
import { AuthService } from '../services/auth.service';

const withToken = (req: HttpRequest<unknown>, token: string | null) =>
    token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

const isAuthCall = (url: string) => url.includes('/api/auth/');

/**
 * Adds the access token and transparently renews it (PLAN.md 2.9): on a 401 the request is retried
 * once with a refreshed token. If refreshing fails the session is cleared and the user is sent to /login.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
    const storageService = inject(StorageService);
    const authService = inject(AuthService);
    const router = inject(Router);

    return next(withToken(req, storageService.getToken())).pipe(
        catchError((err: unknown) => {
            const canRefresh =
                err instanceof HttpErrorResponse &&
                err.status === 401 &&
                !isAuthCall(req.url) &&
                !!storageService.getRefreshToken();
            if (!canRefresh) {
                return throwError(() => err);
            }
            return authService.refreshAccessToken().pipe(
                switchMap((token) => next(withToken(req, token))),
                catchError((retryErr: unknown) => {
                    const refreshFailed =
                        retryErr instanceof HttpErrorResponse && !!retryErr.url && isAuthCall(retryErr.url);
                    if (refreshFailed) {
                        authService.clearSession();
                        router.navigate(['/login']);
                        return throwError(() => err);
                    }
                    return throwError(() => retryErr);
                })
            );
        })
    );
};
