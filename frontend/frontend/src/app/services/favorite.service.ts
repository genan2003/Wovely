import { Injectable, signal, inject, PLATFORM_ID, computed, effect } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { StorageService } from './storage.service';
import { Product } from '../models/product.model';

@Injectable({
    providedIn: 'root'
})
export class FavoriteService {
    private platformId = inject(PLATFORM_ID);
    private storageService = inject(StorageService);
    private favorites = signal<Product[]>([]);

    public getFavorites = computed(() => this.favorites());

    constructor() {
        // Initial load
        this.loadFavorites();
        
        // Listen for login/logout to reload favorites
        effect(() => {
            if (this.storageService.isLoggedIn()) {
                this.loadFavorites();
            } else {
                this.favorites.set([]);
            }
        });
    }

    private loadFavorites(): void {
        if (isPlatformBrowser(this.platformId)) {
            const user = this.storageService.getUser();
            if (user && user.id) {
                const key = `favorites_${user.id}`;
                const data = localStorage.getItem(key);
                this.favorites.set(data ? JSON.parse(data) : []);
            }
        }
    }

    private saveFavorites(favs: Product[]): void {
        if (isPlatformBrowser(this.platformId)) {
            const user = this.storageService.getUser();
            if (user && user.id) {
                const key = `favorites_${user.id}`;
                localStorage.setItem(key, JSON.stringify(favs));
            }
        }
        this.favorites.set(favs);
    }

    public toggleFavorite(product: Product): void {
        const current = this.favorites();
        const index = current.findIndex(p => p.id === product.id);
        if (index > -1) {
            this.saveFavorites(current.filter(p => p.id !== product.id));
        } else {
            this.saveFavorites([...current, product]);
        }
    }

    public isFavorite(productId: string): boolean {
        return this.favorites().some(p => p.id === productId);
    }

    public removeFavorite(productId: string): void {
        const current = this.favorites();
        this.saveFavorites(current.filter(p => p.id !== productId));
    }
}
