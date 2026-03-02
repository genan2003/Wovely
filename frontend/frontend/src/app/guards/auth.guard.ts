import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { StorageService } from '../services/storage.service';

export const authGuard: CanActivateFn = (route, state) => {
    const storageService = inject(StorageService);
    const router = inject(Router);

    if (storageService.isLoggedIn()) {
        return true;
    }

    // Redirect to login page
    return router.parseUrl('/login');
};
