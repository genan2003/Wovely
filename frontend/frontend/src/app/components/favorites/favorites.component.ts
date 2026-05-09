import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FavoriteService } from '../../services/favorite.service';
import { Product } from '../../models/product.model';

@Component({
    selector: 'app-favorites',
    standalone: true,
    imports: [CommonModule, RouterModule],
    templateUrl: './favorites.component.html',
    styleUrl: './favorites.component.css'
})
export class FavoritesComponent {
    public favoriteService = inject(FavoriteService);
    public favorites = this.favoriteService.getFavorites;

    // Group favorites by shop/seller for the UI layout in favorites.png
    get groupedFavorites() {
        const groups: { [key: string]: Product[] } = {};
        this.favorites().forEach(p => {
            const sellerId = p.sellerId || 'Other';
            if (!groups[sellerId]) groups[sellerId] = [];
            groups[sellerId].push(p);
        });
        return Object.keys(groups).map(key => ({
            sellerId: key,
            sellerName: groups[key][0].sellerName || 'Shop name', // Assuming sellerName exists or fallback
            items: groups[key]
        }));
    }

    remove(productId: string) {
        this.favoriteService.removeFavorite(productId);
    }
}
