import { Injectable, signal, effect, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Product } from '../models/product.model';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { ProductService } from './product.service';

export interface CartItem extends Product {
    quantity: number;
    shippingCost?: number;
    shippingName?: string;
}

export interface CartGroup {
    sellerId: string;
    sellerName: string;
    items: CartItem[];
    deliveryCost: number;
    deliveryMethod: string;
    selectedShipping?: any;
    shippingOptions: any[];
}

@Injectable({
    providedIn: 'root'
})
export class CartService {
    private platformId = inject(PLATFORM_ID);
    public items = signal<CartItem[]>([]);
    private productService = inject(ProductService);

    constructor() {
        if (isPlatformBrowser(this.platformId)) {
            // Load cart from local storage
            const savedCart = localStorage.getItem('wovely_cart');
            if (savedCart) {
                this.items.set(JSON.parse(savedCart));
            }

            // Persist cart to local storage whenever it changes
            effect(() => {
                localStorage.setItem('wovely_cart', JSON.stringify(this.items()));
            });
        }
    }

    addToCart(product: Product, shipping?: { name: string, price: number }) {
        this.items.update(currentItems => {
            const existingItem = currentItems.find(item => item.id === product.id);
            if (existingItem) {
                return currentItems.map(item => 
                    item.id === product.id 
                        ? { 
                            ...item, 
                            quantity: item.quantity + 1,
                            shippingCost: shipping?.price ?? item.shippingCost ?? 15,
                            shippingName: shipping?.name ?? item.shippingName ?? 'Standard Eco-Delivery'
                          } 
                        : item
                );
            }
            return [...currentItems, { 
                ...product, 
                quantity: 1, 
                shippingCost: shipping?.price ?? 15,
                shippingName: shipping?.name ?? 'Standard Eco-Delivery'
            }];
        });
    }

    getGroupedItems(): CartGroup[] {
        const items = this.items();
        const groups: { [key: string]: CartGroup } = {};
        
        items.forEach(item => {
            const sellerId = item.sellerId;
            if (!groups[sellerId]) {
                const defaultOptions = [
                    { id: 'eco-1', name: 'Bicycle Delivery', price: 10, co2: 0.1 },
                    { id: 'eco-2', name: 'Electric Van', price: 15, co2: 0.5 },
                    { id: 'eco-3', name: 'Carbon Neutral Courier', price: 25, co2: 1.2 }
                ];
                
                groups[sellerId] = {
                    sellerId: sellerId,
                    sellerName: item.sellerName || 'Artisan Shop',
                    items: [],
                    deliveryCost: item.shippingCost ?? 15,
                    deliveryMethod: item.shippingName ?? 'Electric Van',
                    shippingOptions: defaultOptions,
                    selectedShipping: defaultOptions.find(o => o.name === (item.shippingName ?? 'Electric Van')) || defaultOptions[1]
                };
            }
            groups[sellerId].items.push(item);
        });
        
        return Object.values(groups);
    }

    setShippingForSeller(sellerId: string, option: any) {
        this.items.update(currentItems => {
            return currentItems.map(item => {
                if (item.sellerId === sellerId) {
                    return {
                        ...item,
                        shippingCost: option.price,
                        shippingName: option.name
                    };
                }
                return item;
            });
        });
    }

    removeFromCart(productId: string) {
        this.items.update(currentItems => 
            currentItems.filter(item => item.id !== productId)
        );
    }

    updateQuantity(productId: string, quantity: number) {
        if (quantity <= 0) {
            this.removeFromCart(productId);
            return;
        }
        this.items.update(currentItems =>
            currentItems.map(item =>
                item.id === productId ? { ...item, quantity } : item
            )
        );
    }

    clearCart() {
        this.items.set([]);
    }

    removeItemsBySeller(sellerId: string) {
        this.items.update(currentItems =>
            currentItems.filter(item => item.sellerId !== sellerId)
        );
    }

    getTotalPrice(): number {
        return this.items().reduce((total, item) => total + (item.price * item.quantity), 0);
    }

    getItemCount(): number {
        return this.items().reduce((total, item) => total + item.quantity, 0);
    }

    /**
     * Removes cart items that can no longer be bought (archived, not approved or deleted — PLAN.md 2.8)
     * and emits the removed items. Network/server errors keep the item (the server re-validates anyway).
     */
    pruneUnavailable(): Observable<CartItem[]> {
        const items = this.items();
        if (items.length === 0) {
            return of([]);
        }
        return forkJoin(
            items.map((item) =>
                this.productService.getProductById(item.id!).pipe(
                    map((p) => ({ item, ok: p?.status === 'APPROVED' })),
                    catchError((err: HttpErrorResponse) => of({ item, ok: err.status !== 404 }))
                )
            )
        ).pipe(
            map((results) => {
                const removed = results.filter((r) => !r.ok).map((r) => r.item);
                if (removed.length > 0) {
                    const ids = new Set(removed.map((r) => r.id));
                    this.items.update((list) => list.filter((x) => !ids.has(x.id)));
                }
                return removed;
            })
        );
    }
}
