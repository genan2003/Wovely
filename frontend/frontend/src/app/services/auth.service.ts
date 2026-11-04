import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, finalize, map, shareReplay, tap } from 'rxjs/operators';
import { StorageService } from './storage.service';
import { environment } from '../../environments/environment';

const AUTH_API = environment.apiUrl + '/auth/';

const httpOptions = {
    headers: new HttpHeaders({ 'Content-Type': 'application/json' })
};

@Injectable({
    providedIn: 'root',
})
export class AuthService {
    private http = inject(HttpClient);
    private storageService = inject(StorageService);

    // Global authentication state using signal
    public isLoggedIn = signal<boolean>(this.storageService.isLoggedIn());

    /** In-flight refresh shared by all requests that hit a 401 at the same time. */
    private refresh$: Observable<string> | null = null;

    login(username: string, password: string): Observable<any> {
        return this.http.post(AUTH_API + 'signin', { username, password }, httpOptions).pipe(
            tap((data: any) => this.storeSession(data))
        );
    }

    /**
     * Exchanges the refresh token for a new (short-lived) access token and a rotated refresh token.
     * Concurrent callers share one request. Errors propagate; the caller decides to sign out.
     */
    refreshAccessToken(): Observable<string> {
        if (!this.refresh$) {
            this.refresh$ = this.http
                .post<any>(AUTH_API + 'refresh', { refreshToken: this.storageService.getRefreshToken() }, httpOptions)
                .pipe(
                    tap((data: any) => this.storeSession(data)),
                    map((data: any) => data.accessToken as string),
                    finalize(() => (this.refresh$ = null)),
                    shareReplay(1)
                );
        }
        return this.refresh$;
    }

    /** Drops the local session without calling the server (used when refreshing fails). */
    clearSession(): void {
        this.storageService.clean();
        this.isLoggedIn.set(false);
    }

    private storeSession(data: any): void {
        if (!data?.accessToken) {
            return;
        }
        const { refreshToken, ...user } = data;
        this.storageService.saveToken(data.accessToken);
        if (refreshToken) {
            this.storageService.saveRefreshToken(refreshToken);
        }
        this.storageService.saveUser(user);
        this.isLoggedIn.set(true);
    }

    register(username: string, email: string, password: string): Observable<any> {
        return this.http.post(AUTH_API + 'signup', { username, email, password }, httpOptions);
    }

    /** Revokes the refresh token on the server (best effort) and clears the local session. */
    logout(): Observable<any> {
        const refreshToken = this.storageService.getRefreshToken();
        const revoke = refreshToken
            ? this.http.post(AUTH_API + 'signout', { refreshToken }, httpOptions).pipe(catchError(() => of(null)))
            : of(null);
        return revoke.pipe(finalize(() => this.clearSession()));
    }
}
