import { Injectable, signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { InventoryItem, InventoryDashboard, InventoryResponse } from '../models/inventory.model';
import { ManualOrderRequest, ManualOrderResponse } from '../models/manual-order.model';

const PRODUCTS_API = 'http://localhost:8082/api/products';
const SELLER_API = 'http://localhost:8081/api/seller';

@Injectable({ providedIn: 'root' })
export class InventoryService {
    private http = inject(HttpClient);

    // Signals for reactive state
    inventoryItems = signal<InventoryItem[]>([]);
    loading = signal<boolean>(false);
    error = signal<string | null>(null);
    dashboard = signal<InventoryDashboard | null>(null);

    /**
     * Get all inventory items for a seller (visual, SKU-free interface).
     * Now fetches from products service which stores stock quantities.
     */
    getInventory(sellerId: string): Observable<InventoryResponse> {
        this.loading.set(true);
        this.error.set(null);
        return this.http.get<InventoryResponse>(`${PRODUCTS_API}/seller/${sellerId}`);
    }

    /**
     * Get inventory dashboard summary.
     */
    getDashboard(sellerId: string): Observable<InventoryDashboard> {
        return this.http.get<InventoryDashboard>(`${PRODUCTS_API}/seller/${sellerId}/dashboard`);
    }

    /**
     * Get low stock alerts.
     */
    getLowStockItems(sellerId: string): Observable<{ items: InventoryItem[]; alertType: string }> {
        return this.http.get<{ items: InventoryItem[]; alertType: string }>(
            `${PRODUCTS_API}/seller/${sellerId}/low-stock`
        );
    }

    /**
     * Get out of stock items.
     */
    getOutOfStockItems(sellerId: string): Observable<{ items: InventoryItem[]; alertType: string }> {
        return this.http.get<{ items: InventoryItem[]; alertType: string }>(
            `${PRODUCTS_API}/seller/${sellerId}/out-of-stock`
        );
    }

    /**
     * Get a specific inventory item by product ID.
     */
    getInventoryItem(sellerId: string, productId: string): Observable<InventoryItem> {
        return this.http.get<InventoryItem>(`${PRODUCTS_API}/seller/${sellerId}/product/${productId}`);
    }

    /**
     * Update stock quantity for a product.
     */
    updateStock(sellerId: string, productId: string, quantity: number): Observable<InventoryItem> {
        return this.http.put<InventoryItem>(`${PRODUCTS_API}/seller/${sellerId}/product/${productId}/stock`, {
            quantity
        });
    }

    /**
     * Restock a product (add to existing stock).
     */
    restockProduct(sellerId: string, productId: string, quantity: number): Observable<InventoryItem> {
        return this.http.post<InventoryItem>(
            `${PRODUCTS_API}/seller/${sellerId}/product/${productId}/restock`,
            { quantity }
        );
    }

    /**
     * Add a new product to inventory (creates product in products service).
     */
    addProduct(sellerId: string, item: Partial<InventoryItem>): Observable<any> {
        const product = {
            name: item.productName,
            description: item.productName || '',
            price: item.price || 0,
            sellerId: sellerId,
            imageUrl: item.imageUrl || '',
            category: item.category || 'Uncategorized',
            co2EmissionScore: item.co2EmissionScore || 'Low',
            shippingMethod: item.shippingMethod || 'Standard',
            isHandmade: item.isHandmade ?? true,
            stockQuantity: item.stockQuantity || 0,
            lowStockThreshold: item.lowStockThreshold || 5,
            status: 'PENDING'
        };
        return this.http.post(`${PRODUCTS_API}`, product);
    }

    /**
     * Remove a product from inventory (not implemented - products are never deleted).
     */
    removeProduct(sellerId: string, productId: string): Observable<{ message: string; productId: string }> {
        throw new Error('Product removal not supported');
    }

    /**
     * Get products by category for seller.
     */
    getProductsByCategory(sellerId: string, category: string): Observable<{ items: InventoryItem[]; category: string }> {
        return this.http.get<{ items: InventoryItem[]; category: string }>(
            `${PRODUCTS_API}/seller/${sellerId}/category/${category}`
        );
    }

    /**
     * Create a manual order for off-platform sales.
     */
    createManualOrder(request: ManualOrderRequest): Observable<ManualOrderResponse> {
        return this.http.post<ManualOrderResponse>(`${SELLER_API}/orders/manual`, request);
    }

    /**
     * Get orders by seller ID.
     */
    getOrdersBySeller(sellerId: string): Observable<{ orders: any[]; totalOrders: number }> {
        return this.http.get<{ orders: any[]; totalOrders: number }>(`${SELLER_API}/orders/seller/${sellerId}`);
    }
}
