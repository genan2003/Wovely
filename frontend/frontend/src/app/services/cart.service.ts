import { Injectable, signal, effect, inject, untracked } from '@angular/core';
import { Product } from '../models/product.model';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { ProductService } from './product.service';
import { AuthService } from './auth.service';
import { DeliveryOptionService } from './delivery-option.service';
import { DeliveryOptionId } from '../models/delivery-option';

/** Guests keep the original key, so a cart built before this change survives. */
const GUEST_CART_KEY = 'wovely_cart';
const cartKey = (userId: string | undefined) => (userId ? `${GUEST_CART_KEY}_${userId}` : GUEST_CART_KEY);

export interface ShippingOption {
    id: DeliveryOptionId;
    name: string;
    price: number;
    co2: number;
}

export interface CartItem extends Product {
    quantity: number;
    /** Delivery chosen for this item's seller; the server prices it at checkout. */
    deliveryOptionId?: DeliveryOptionId;
}

export interface CartGroup {
    sellerId: string;
    sellerName: string;
    items: CartItem[];
    deliveryCost: number;
    deliveryMethod: string;
    selectedShipping?: ShippingOption;
    shippingOptions: ShippingOption[];
}

@Injectable({
    providedIn: 'root'
})
export class CartService {
    private productService = inject(ProductService);
    private auth = inject(AuthService);
    private delivery = inject(DeliveryOptionService);

    /**
     * Where the current items are persisted; follows the signed-in user (PLAN.md 5.6). It starts on
     * the guest cart, so a returning signed-in user also picks up anything left under the old shared key.
     */
    private activeKey = GUEST_CART_KEY;
    public items = signal<CartItem[]>(this.read(GUEST_CART_KEY));

    constructor() {
        effect(() => {
            const key = cartKey(this.auth.user()?.id);
            untracked(() => this.switchTo(key));
        });
        effect(() => localStorage.setItem(this.activeKey, JSON.stringify(this.items())));
    }

    private read(key: string): CartItem[] {
        const saved = localStorage.getItem(key);
        return saved ? (JSON.parse(saved) as CartItem[]) : [];
    }

    /**
     * Logging in carries what a guest collected into that user's own cart; logging out
     * leaves the user's cart behind, so the next person on this browser can't see it.
     */
    private switchTo(key: string): void {
        if (key === this.activeKey) {
            return;
        }
        const isLogin = this.activeKey === GUEST_CART_KEY;
        const guestItems = isLogin ? this.items() : [];
        if (isLogin) {
            localStorage.removeItem(GUEST_CART_KEY);
        }
        this.activeKey = key;
        this.items.set(this.merge(this.read(key), guestItems));
    }

    private merge(saved: CartItem[], incoming: CartItem[]): CartItem[] {
        const merged = saved.map((item) => {
            const match = incoming.find((other) => other.id === item.id);
            return match ? { ...item, quantity: item.quantity + match.quantity } : item;
        });
        return [...merged, ...incoming.filter((item) => !saved.some((other) => other.id === item.id))];
    }

    addToCart(product: Product) {
        this.items.update(currentItems => {
            const existingItem = currentItems.find(item => item.id === product.id);
            if (existingItem) {
                return currentItems.map(item =>
                    item.id === product.id ? { ...item, quantity: item.quantity + 1 } : item
                );
            }
            return [...currentItems, { ...product, quantity: 1 }];
        });
    }

    /** One group per seller (one order each), with the server's delivery options and the chosen one. */
    getGroupedItems(): CartGroup[] {
        const options: ShippingOption[] = this.delivery.options()
            .map(o => ({ id: o.id, name: o.label, price: o.fee, co2: o.co2Kg }));
        const fallback = this.delivery.defaultOption();
        const groups: { [key: string]: CartGroup } = {};

        this.items().forEach(item => {
            const sellerId = item.sellerId;
            if (!groups[sellerId]) {
                const selected = options.find(o => o.id === item.deliveryOptionId)
                    ?? options.find(o => o.id === fallback?.id);
                groups[sellerId] = {
                    sellerId,
                    sellerName: item.sellerName || 'Artisan Shop',
                    items: [],
                    deliveryCost: selected?.price ?? 0,
                    deliveryMethod: selected?.name ?? '',
                    shippingOptions: options,
                    selectedShipping: selected,
                };
            }
            groups[sellerId].items.push(item);
        });

        return Object.values(groups);
    }

    setShippingForSeller(sellerId: string, option: ShippingOption) {
        this.items.update(currentItems =>
            currentItems.map(item => item.sellerId === sellerId ? { ...item, deliveryOptionId: option.id } : item)
        );
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
