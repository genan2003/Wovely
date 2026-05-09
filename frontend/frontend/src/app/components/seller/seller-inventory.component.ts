import { Component, OnInit, signal, computed, inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { InventoryService } from '../../services/inventory.service';
import { InventoryItem } from '../../models/inventory.model';
import { StorageService } from '../../services/storage.service';
import { HttpErrorResponse } from '@angular/common/http';
import { CategoryService } from '../../services/category.service';
import { Category } from '../../models/category.model';
import { forkJoin, map, switchMap, of, catchError } from 'rxjs';

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
    categoryService = inject(CategoryService);
    private platformId = inject(PLATFORM_ID);

    // Categories data
    categoryTree = signal<any[]>([]);
    selectedCategoryPath = signal<string>('');
    selectedCategoryName = signal<string>('');

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
    formCo2Score = signal<string>('Low');
    formShipping = signal<string>('Standard');
    formIsHandmade = signal<boolean>(true);
    formStockQuantity = signal<number>(0);
    formLowStockThreshold = signal<number>(5);
    formCity = signal<string>('');
    formRegion = signal<string>('');

    // Edit/Restock Dialog
    showRestockDialog = signal<boolean>(false);
    selectedItem = signal<InventoryItem | null>(null);
    restockQuantity = signal<number>(0);
    restockLoading = signal<boolean>(false);
    restockError = signal<string | null>(null);
    isEditMode = signal<boolean>(false); // true for direct edit, false for restock (add)

    // Edit form fields
    editName = signal<string>('');
    editPrice = signal<number>(0);
    editDescription = signal<string>('');
    editImageUrl = signal<string>('');
    editLowStockThreshold = signal<number>(5);
    editCity = signal<string>('');
    editRegion = signal<string>('');

    // Details Dialog
    showDetailsDialog = signal<boolean>(false);
    selectedDetailItem = signal<InventoryItem | null>(null);

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
                (item.productName || '').toLowerCase().includes(query)
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
            
            if (isPlatformBrowser(this.platformId)) {
                this.loadCategoryTree();
            }
        }
    }

    loadCategoryTree(): void {
        this.categoryService.getRootCategories().pipe(
            switchMap(roots => {
                if (!roots || roots.length === 0) return of([]);
                const obs = roots.map(root => 
                    this.categoryService.getSubCategories(root.id).pipe(
                        switchMap(subs => {
                            if (!subs || subs.length === 0) return of({ ...root, children: [] });
                            const subObs = subs.map(sub => 
                                this.categoryService.getSubCategories(sub.id).pipe(
                                    map(leafs => ({ ...sub, children: leafs || [] })),
                                    catchError(() => of({ ...sub, children: [] }))
                                )
                            );
                            return forkJoin(subObs).pipe(
                                map(subsWithLeafs => ({ ...root, children: subsWithLeafs }))
                            );
                        }),
                        catchError(() => of({ ...root, children: [] }))
                    )
                );
                return forkJoin(obs);
            }),
            catchError(err => {
                console.error('Failed to load category tree', err);
                return of(this.getMockCategoryTree());
            })
        ).subscribe((data: any[]) => {
            if (!data || data.length === 0) {
                this.categoryTree.set(this.getMockCategoryTree());
            } else {
                this.categoryTree.set(data);
            }
        });
    }

    private getMockCategoryTree(): any[] {
        return [
            {
                id: 'm1', name: 'Home & Living', children: [
                    { id: 'm1-1', name: 'Ceramics', children: [{ id: 'm1-1-1', name: 'Mugs' }, { id: 'm1-1-2', name: 'Plates' }] },
                    { id: 'm1-2', name: 'Decor', children: [{ id: 'm1-2-1', name: 'Candles' }, { id: 'm1-2-2', name: 'Wall Art' }] }
                ]
            },
            {
                id: 'm2', name: 'Clothing', children: [
                    { id: 'm2-1', name: 'Women', children: [{ id: 'm2-1-1', name: 'Dresses' }, { id: 'm2-1-2', name: 'Tops' }] },
                    { id: 'm2-2', name: 'Accessories', children: [{ id: 'm2-2-1', name: 'Bags' }, { id: 'm2-2-2', name: 'Scarves' }] }
                ]
            }
        ];
    }

    selectCategory(root: string, sub?: string, leaf?: string, event?: Event): void {
        if (event) {
            event.preventDefault();
            event.stopPropagation();
        }
        const path = [root, sub, leaf].filter(v => !!v).join(' > ');
        this.selectedCategoryPath.set(path);
        this.selectedCategoryName.set(leaf || sub || root);
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
        this.formCo2Score.set('Low');
        this.formShipping.set('Standard');
        this.formIsHandmade.set(true);
        this.formStockQuantity.set(0);
        this.formLowStockThreshold.set(5);
        this.formCity.set('');
        this.formRegion.set('');
        this.addError.set(null);

        this.selectedCategoryPath.set('');
        this.selectedCategoryName.set('');
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

        if (!this.selectedCategoryPath()) {
            this.addError.set('Please select a category.');
            return;
        }

        this.addLoading.set(true);

        const newItem: Partial<InventoryItem> = {
            productName: this.formName(),
            description: this.formDescription(),
            price: this.formPrice(),
            imageUrl: this.formImageUrl(),
            category: this.selectedCategoryName(),
            categoryPath: this.selectedCategoryPath(),
            co2EmissionScore: this.formCo2Score(),
            shippingMethod: this.formShipping(),
            isHandmade: this.formIsHandmade(),
            stockQuantity: this.formStockQuantity(),
            lowStockThreshold: this.formLowStockThreshold(),
            city: this.formCity(),
            region: this.formRegion()
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

    // Stock Management Methods
    openRestockDialog(item: InventoryItem): void {
        this.selectedItem.set(item);
        this.restockQuantity.set(0);
        this.restockError.set(null);
        this.isEditMode.set(false);
        this.showRestockDialog.set(true);
    }

    openEditStockDialog(item: InventoryItem): void {
        this.selectedItem.set(item);
        this.restockQuantity.set(item.stockQuantity);
        this.editName.set(item.productName);
        this.editPrice.set(item.price);
        this.editDescription.set(item.description || '');
        this.editImageUrl.set(item.imageUrl || '');
        this.editLowStockThreshold.set(item.lowStockThreshold || 5);
        this.editCity.set(item.city || '');
        this.editRegion.set(item.region || '');
        this.restockError.set(null);
        this.isEditMode.set(true);
        this.showRestockDialog.set(true);
    }

    closeRestockDialog(): void {
        this.showRestockDialog.set(false);
        this.selectedItem.set(null);
    }

    saveStockUpdate(): void {
        const item = this.selectedItem();
        if (!item) return;

        this.restockLoading.set(true);
        this.restockError.set(null);

        if (this.isEditMode()) {
            const updatedItem: Partial<InventoryItem> = {
                ...item, // Preserve existing fields
                productName: this.editName(),
                price: this.editPrice(),
                description: this.editDescription(),
                imageUrl: this.editImageUrl(),
                lowStockThreshold: this.editLowStockThreshold(),
                stockQuantity: this.restockQuantity(),
                city: this.editCity(),
                region: this.editRegion()
            };

            // Single update call handles everything including stock, preventing race conditions
            this.inventoryService.updateProduct(this.sellerId, item.productId, updatedItem).subscribe({
                next: () => {
                    this.finishStockUpdate();
                },
                error: (err) => {
                    this.restockLoading.set(false);
                    this.restockError.set('Failed to update product details.');
                    console.error(err);
                }
            });
        } else {
            // Simple restock (add quantity)
            if (this.restockQuantity() <= 0) {
                this.restockError.set('Please enter a valid quantity.');
                this.restockLoading.set(false);
                return;
            }
            this.inventoryService.restockProduct(this.sellerId, item.productId, this.restockQuantity()).subscribe({
                next: () => this.finishStockUpdate(),
                error: (err) => {
                    this.restockLoading.set(false);
                    this.restockError.set('Failed to restock product.');
                    console.error(err);
                }
            });
        }
    }

    private finishStockUpdate(): void {
        this.restockLoading.set(false);
        this.showRestockDialog.set(false);
        this.loadInventory();
        this.loadDashboard();
    }

    deleteProduct(item: InventoryItem): void {
        if (confirm(`Are you sure you want to remove "${item.productName}" from your inventory?`)) {
            this.inventoryService.removeProduct(this.sellerId, item.productId).subscribe({
                next: () => {
                    this.loadInventory();
                    this.loadDashboard();
                },
                error: (err) => {
                    alert('Failed to delete product. Please try again.');
                    console.error(err);
                }
            });
        }
    }

    // Details Methods
    viewDetails(item: InventoryItem): void {
        this.selectedDetailItem.set(item);
        this.showDetailsDialog.set(true);
    }

    closeDetailsDialog(): void {
        this.showDetailsDialog.set(false);
        this.selectedDetailItem.set(null);
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
