import { Injectable } from '@angular/core';
import { AuthUser } from '../models/auth.model';

const USER_KEY = 'auth-user';
const TOKEN_KEY = 'auth-token';
const REFRESH_KEY = 'auth-refresh';

/** The session lives in sessionStorage: it ends with the tab and is never shared between users. */
@Injectable({ providedIn: 'root' })
export class StorageService {
  clean(): void {
    sessionStorage.clear();
  }

  saveToken(token: string): void {
    sessionStorage.setItem(TOKEN_KEY, token);
  }

  getToken(): string | null {
    return sessionStorage.getItem(TOKEN_KEY);
  }

  /** Opaque refresh token (PLAN.md 2.9); rotated by the server on every refresh. */
  saveRefreshToken(token: string): void {
    sessionStorage.setItem(REFRESH_KEY, token);
  }

  getRefreshToken(): string | null {
    return sessionStorage.getItem(REFRESH_KEY);
  }

  saveUser(user: AuthUser): void {
    sessionStorage.setItem(USER_KEY, JSON.stringify(user));
  }

  getUser(): AuthUser | null {
    const user = sessionStorage.getItem(USER_KEY);
    return user ? (JSON.parse(user) as AuthUser) : null;
  }

  isLoggedIn(): boolean {
    return sessionStorage.getItem(USER_KEY) !== null;
  }
}
