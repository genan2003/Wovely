import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

const USER_KEY = 'auth-user';
const TOKEN_KEY = 'auth-token';

@Injectable({
  providedIn: 'root'
})
export class StorageService {
  private platformId = inject(PLATFORM_ID);

  constructor() { }

  clean(): void {
    if (isPlatformBrowser(this.platformId)) {
      window.sessionStorage.clear();
    }
  }

  public saveToken(token: string): void {
    if (isPlatformBrowser(this.platformId)) {
      window.sessionStorage.removeItem(TOKEN_KEY);
      window.sessionStorage.setItem(TOKEN_KEY, token);
    }
  }

  public getToken(): string | null {
    if (isPlatformBrowser(this.platformId)) {
      return window.sessionStorage.getItem(TOKEN_KEY);
    }
    return null;
  }

  public saveUser(user: any): void {
    if (isPlatformBrowser(this.platformId)) {
      window.sessionStorage.removeItem(USER_KEY);
      window.sessionStorage.setItem(USER_KEY, JSON.stringify(user));
    }
  }

  public getUser(): any {
    if (isPlatformBrowser(this.platformId)) {
      const user = window.sessionStorage.getItem(USER_KEY);
      if (user) {
        return JSON.parse(user);
      }
    }
    return null;
  }

  public isLoggedIn(): boolean {
    if (isPlatformBrowser(this.platformId)) {
      const user = window.sessionStorage.getItem(USER_KEY);
      return user !== null;
    }
    return false;
  }
}
