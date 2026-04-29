import { Routes } from '@angular/router';
import { SellerInventoryComponent } from './seller-inventory.component';
import { ManualOrderComponent } from './manual-order.component';
import { SellerOrdersComponent } from './seller-orders.component';

export const SELLER_ROUTES: Routes = [
    { path: '', redirectTo: 'inventory', pathMatch: 'full' },
    { path: 'inventory', component: SellerInventoryComponent },
    { path: 'manual-order', component: ManualOrderComponent },
    { path: 'orders', component: SellerOrdersComponent }
];
