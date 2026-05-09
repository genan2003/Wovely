import { CanActivateFn, Router } from '@angular/router';
import { inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { StorageService } from '../services/storage.service';

export const authGuard: CanActivateFn = (route, state) => {
    const storageService = inject(StorageService);
    const router = inject(Router);
    const platformId = inject(PLATFORM_ID);

    if (isPlatformBrowser(platformId)) {
        if (storageService.isLoggedIn()) {
            return true;
        }
        // Redirect to login page only on browser
        return router.parseUrl('/login');
    }

    // On server, allow it to pass so the browser can check correctly
    return true;
};
