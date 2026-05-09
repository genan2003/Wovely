import { Routes } from '@angular/router';
import { LoginComponent } from './components/login/login.component';
import { RegisterComponent } from './components/register/register.component';
import { ProductListComponent } from './components/product-list/product-list.component';
import { authGuard } from './guards/auth.guard';
import { AdminGuard } from './guards/admin.guard';

export const routes: Routes = [
    { path: 'login', component: LoginComponent },
    { path: 'register', component: RegisterComponent },
    { path: 'products', component: ProductListComponent },
    { path: 'products/:id', loadComponent: () => import('./components/product-details/product-details.component').then(m => m.ProductDetailsComponent) },
    { path: 'cart', loadComponent: () => import('./components/cart/cart.component').then(m => m.CartComponent) },
    { path: 'reviews', loadComponent: () => import('./components/reviews/reviews.component').then(m => m.ReviewsComponent), canActivate: [authGuard] },
    { 
        path: 'seller', 
        loadChildren: () => import('./components/seller/seller.routes').then(m => m.SELLER_ROUTES),
        canActivate: [authGuard] 
    },
    { path: 'admin', loadComponent: () => import('./components/admin/admin.component').then(m => m.AdminComponent), canActivate: [AdminGuard] },
    { path: 'settings', loadComponent: () => import('./components/settings/settings.component').then(m => m.SettingsComponent), canActivate: [authGuard] },
    { path: 'review-order/:id', loadComponent: () => import('./components/review-order/review-order.component').then(m => m.ReviewOrderComponent), canActivate: [authGuard] },
    { path: 'messages', loadComponent: () => import('./components/message-center/message-center.component').then(m => m.MessageCenterComponent), canActivate: [authGuard] },
    { path: 'seller-profile/:id', loadComponent: () => import('./components/seller-profile/seller-profile.component').then(m => m.SellerProfileComponent) },
    { path: '', redirectTo: 'products', pathMatch: 'full' }
];
