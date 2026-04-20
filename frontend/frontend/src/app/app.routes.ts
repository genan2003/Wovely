import { Routes } from '@angular/router';
import { LoginComponent } from './components/login/login.component';
import { RegisterComponent } from './components/register/register.component';
import { ProductListComponent } from './components/product-list/product-list.component';
import { authGuard } from './guards/auth.guard';
import { AdminGuard } from './guards/admin.guard';

export const routes: Routes = [
    { path: 'login', component: LoginComponent },
    { path: 'register', component: RegisterComponent },
    { path: 'products', component: ProductListComponent, canActivate: [authGuard] },
    { 
        path: 'seller', 
        loadChildren: () => import('./components/seller/seller.routes').then(m => m.SELLER_ROUTES),
        canActivate: [authGuard] 
    },
    { path: 'admin', loadComponent: () => import('./components/admin/admin.component').then(m => m.AdminComponent), canActivate: [AdminGuard] },
    { path: '', redirectTo: 'products', pathMatch: 'full' }
];
