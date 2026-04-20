import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { InventoryService } from '../../services/inventory.service';
import { InventoryItem } from '../../models/inventory.model';
import { StorageService } from '../../services/storage.service';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
    selector: 'app-seller-inventory',
    standalone: true,
    imports: [CommonModule, FormsModule, RouterModule],
    templateUrl: './seller-inventory.component.html',
    styleUrls: ['./seller-inventory.component.css']
})
export class SellerInventoryComponent implements OnInit {
    inventoryService = inject(InventoryService);
    storageService = inject(StorageService);

    // Signals
    items = signal<InventoryItem[]>([]);
    loading = signal<boolean>(true);
    error = signal<string | null>(null);
    dashboard = signal<any>(null);
    selectedCategory = signal<string>('all');
    searchQuery = signal<string>('');
    viewMode = signal<'grid' | 'list'>('grid');

    // Add Product Dialog
    showAddDialog = signal<boolean>(false);
    addLoading = signal<boolean>(false);
    addError = signal<string | null>(null);
    
    // Add Product Form Fields
    formName = signal<string>('');
    formDescription = signal<string>('');
    formPrice = signal<number>(0);
    formImageUrl = signal<string>('');
    formCategory = signal<string>('Handmade');
    formCo2Score = signal<string>('Low');
    formShipping = signal<string>('Standard');
    formIsHandmade = signal<boolean>(true);
    formStockQuantity = signal<number>(0);
    formLowStockThreshold = signal<number>(5);

    // Computed
    filteredItems = computed(() => {
        let filtered = this.items();

        // Filter by category
        if (this.selectedCategory() !== 'all') {
            filtered = filtered.filter(item => item.category === this.selectedCategory());
        }

        // Filter by search query
        if (this.searchQuery()) {
            const query = this.searchQuery().toLowerCase();
            filtered = filtered.filter(item =>
                item.productName.toLowerCase().includes(query)
            );
        }

        return filtered;
    });

    categories = computed(() => {
        const cats = new Set(this.items().map(item => item.category));
        return ['all', ...Array.from(cats)];
    });

    stats = computed(() => {
        const items = this.items();
        return {
            total: items.length,
            inStock: items.filter(i => i.stockQuantity > 0).length,
            lowStock: items.filter(i => i.isLowStock).length,
            outOfStock: items.filter(i => i.isOutOfStock).length
        };
    });

    sellerId: string = '';
    sellerName: string = '';

    ngOnInit(): void {
        const user = this.storageService.getUser();
        if (user) {
            this.sellerId = user.id;
            this.sellerName = user.username;
            this.loadInventory();
            this.loadDashboard();
        }
    }

    loadInventory(): void {
        this.loading.set(true);
        this.inventoryService.getInventory(this.sellerId).subscribe({
            next: (response: { items: InventoryItem[] }) => {
                this.items.set(response.items);
                this.loading.set(false);
            },
            error: (err: HttpErrorResponse) => {
                this.error.set('Failed to load inventory. Please try again.');
                this.loading.set(false);
                console.error(err);
            }
        });
    }

    loadDashboard(): void {
        this.inventoryService.getDashboard(this.sellerId).subscribe({
            next: (data) => {
                this.dashboard.set(data);
            },
            error: (err: HttpErrorResponse) => console.error('Failed to load dashboard:', err)
        });
    }

    filterByCategory(category: string): void {
        this.selectedCategory.set(category);
    }

    search(event: Event): void {
        const target = event.target as HTMLInputElement;
        this.searchQuery.set(target.value);
    }

    toggleView(): void {
        this.viewMode.set(this.viewMode() === 'grid' ? 'list' : 'grid');
    }

    // Add Product Methods
    openAddDialog(): void {
        this.resetForm();
        this.showAddDialog.set(true);
    }

    closeAddDialog(): void {
        this.showAddDialog.set(false);
        this.resetForm();
    }

    resetForm(): void {
        this.formName.set('');
        this.formDescription.set('');
        this.formPrice.set(0);
        this.formImageUrl.set('');
        this.formCategory.set('Handmade');
        this.formCo2Score.set('Low');
        this.formShipping.set('Standard');
        this.formIsHandmade.set(true);
        this.formStockQuantity.set(0);
        this.formLowStockThreshold.set(5);
        this.addError.set(null);
    }

    addProduct(): void {
        this.addError.set(null);

        if (!this.formName()) {
            this.addError.set('Product name is required.');
            return;
        }
        if (this.formPrice() <= 0) {
            this.addError.set('Price must be greater than 0.');
            return;
        }

        this.addLoading.set(true);

        const newItem: Partial<InventoryItem> = {
            productName: this.formName(),
            price: this.formPrice(),
            imageUrl: this.formImageUrl(),
            category: this.formCategory(),
            co2EmissionScore: this.formCo2Score(),
            shippingMethod: this.formShipping(),
            isHandmade: this.formIsHandmade(),
            stockQuantity: this.formStockQuantity(),
            lowStockThreshold: this.formLowStockThreshold()
        };

        this.inventoryService.addProduct(this.sellerId, newItem).subscribe({
            next: () => {
                this.addLoading.set(false);
                this.showAddDialog.set(false);
                this.resetForm();
                this.loadInventory();
                this.loadDashboard();
            },
            error: (err: HttpErrorResponse) => {
                this.addLoading.set(false);
                this.addError.set('Failed to add product. Please try again.');
                console.error(err);
            }
        });
    }

    getStockBadgeClass(item: InventoryItem): string {
        if (item.isOutOfStock) return 'badge-out-of-stock';
        if (item.isLowStock) return 'badge-low-stock';
        return 'badge-in-stock';
    }

    getStockLabel(item: InventoryItem): string {
        if (item.isOutOfStock) return 'Out of Stock';
        if (item.isLowStock) return 'Low Stock';
        return 'In Stock';
    }
}
