import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { Router, CanActivate, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { StorageService } from '../services/storage.service';
import { isPlatformBrowser } from '@angular/common';

@Injectable({
  providedIn: 'root'
})
export class AdminGuard implements CanActivate {

  private platformId = inject(PLATFORM_ID);

  constructor(private storageService: StorageService, private router: Router) {}

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    if (isPlatformBrowser(this.platformId)) {
      if (this.storageService.isLoggedIn()) {
        const user = this.storageService.getUser();
        if (user && user.roles && user.roles.includes('ROLE_ADMIN')) {
          return true;
        }
      }
      this.router.navigate(['/login']);
      return false;
    }
    // Return true on the server so the Express SSR engine doesn't abort the route
    return true;
  }
}
