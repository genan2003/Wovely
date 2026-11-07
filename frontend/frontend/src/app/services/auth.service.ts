import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, finalize, map, shareReplay, tap } from 'rxjs/operators';
import { StorageService } from './storage.service';
import { environment } from '../../environments/environment';
import { AuthResponse, AuthUser, MessageResponse, Role } from '../models/auth.model';

const AUTH_API = environment.apiUrl + '/auth/';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private storageService = inject(StorageService);

  /** The signed-in user. Everything that depends on who is signed in reads this signal. */
  readonly user = signal<AuthUser | null>(this.storageService.getUser());
  readonly isLoggedIn = computed(() => this.user() !== null);
  readonly isAdmin = computed(() => this.hasRole('ROLE_ADMIN'));
  readonly isSeller = computed(() => this.hasRole('ROLE_SELLER'));

  /** In-flight refresh shared by all requests that hit a 401 at the same time. */
  private refresh$: Observable<string> | null = null;

  hasRole(...roles: Role[]): boolean {
    const held = this.user()?.roles ?? [];
    return roles.some((role) => held.includes(role));
  }

  login(username: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(AUTH_API + 'signin', { username, password })
      .pipe(tap((data) => this.storeSession(data)));
  }

  /**
   * Exchanges the refresh token for a new (short-lived) access token and a rotated refresh token.
   * Concurrent callers share one request. Errors propagate; the caller decides to sign out.
   */
  refreshAccessToken(): Observable<string> {
    if (!this.refresh$) {
      this.refresh$ = this.http
        .post<AuthResponse>(AUTH_API + 'refresh', { refreshToken: this.storageService.getRefreshToken() })
        .pipe(
          tap((data) => this.storeSession(data)),
          map((data) => data.accessToken),
          finalize(() => (this.refresh$ = null)),
          shareReplay(1)
        );
    }
    return this.refresh$;
  }

  /** Drops the local session without calling the server (used when refreshing fails). */
  clearSession(): void {
    this.storageService.clean();
    this.user.set(null);
  }

  private storeSession(data: AuthResponse): void {
    if (!data?.accessToken) {
      return;
    }
    const { refreshToken, ...user } = data;
    this.storageService.saveToken(data.accessToken);
    if (refreshToken) {
      this.storageService.saveRefreshToken(refreshToken);
    }
    this.storageService.saveUser(user);
    this.user.set(user);
  }

  /** {@code sell}: also open a maker account (listings still need a moderator's approval). */
  register(username: string, email: string, password: string, sell = false): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(AUTH_API + 'signup', {
      username,
      email,
      password,
      roles: sell ? ['seller'] : ['user'],
    });
  }

  /** Revokes the refresh token on the server (best effort) and clears the local session. */
  logout(): Observable<unknown> {
    const refreshToken = this.storageService.getRefreshToken();
    const revoke = refreshToken
      ? this.http.post(AUTH_API + 'signout', { refreshToken }).pipe(catchError(() => of(null)))
      : of(null);
    return revoke.pipe(finalize(() => this.clearSession()));
  }
}
