import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { AuthService } from './auth.service';
import { Product } from '../models/product.model';

/** Favorites are stored per user in this browser and follow whoever is signed in. */
@Injectable({ providedIn: 'root' })
export class FavoriteService {
  private auth = inject(AuthService);
  private favorites = signal<Product[]>([]);

  public getFavorites = computed(() => this.favorites());

  constructor() {
    effect(() => {
      const userId = this.auth.user()?.id;
      untracked(() => this.favorites.set(userId ? this.read(userId) : []));
    });
  }

  private read(userId: string): Product[] {
    const data = localStorage.getItem(`favorites_${userId}`);
    return data ? (JSON.parse(data) as Product[]) : [];
  }

  private save(favorites: Product[]): void {
    const userId = this.auth.user()?.id;
    if (userId) {
      localStorage.setItem(`favorites_${userId}`, JSON.stringify(favorites));
    }
    this.favorites.set(favorites);
  }

  public toggleFavorite(product: Product): void {
    const current = this.favorites();
    this.save(
      current.some((p) => p.id === product.id)
        ? current.filter((p) => p.id !== product.id)
        : [...current, product]
    );
  }

  public isFavorite(productId: string): boolean {
    return this.favorites().some((p) => p.id === productId);
  }

  public removeFavorite(productId: string): void {
    this.save(this.favorites().filter((p) => p.id !== productId));
  }
}
