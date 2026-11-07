import { ChangeDetectionStrategy, Component, OnInit, signal, computed, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { classifyDelivery } from '../../models/delivery-emission';
import { EmissionBadgeComponent } from '../shared/emission-badge/emission-badge.component';
import { InventoryService } from '../../services/inventory.service';
import { InventoryDashboard, InventoryItem } from '../../models/inventory.model';
import { StorageService } from '../../services/storage.service';
import { HttpErrorResponse } from '@angular/common/http';
import { CategoryService } from '../../services/category.service';
import { Category } from '../../models/category.model';
import { DialogService } from '../../services/dialog.service';
import { ToastService } from '../../services/toast.service';
import { ListingStatusComponent } from '../shared/listing-status/listing-status.component';
import { forkJoin, map, switchMap, of, catchError } from 'rxjs';

type CategoryNode = Category & { children: CategoryNode[] };

import { priceError } from '../../models/money';
@Component({
  selector: 'app-seller-inventory',
  imports: [FormsModule, RouterModule, EmissionBadgeComponent, ListingStatusComponent, DecimalPipe],
  templateUrl: './seller-inventory.component.html',
  styleUrls: ['./seller-inventory.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SellerInventoryComponent implements OnInit {
  inventoryService = inject(InventoryService);
  storageService = inject(StorageService);
  categoryService = inject(CategoryService);
  private dialog = inject(DialogService);
  private toast = inject(ToastService);

  // Categories data
  categoryTree = signal<CategoryNode[]>([]);
  selectedCategoryPath = signal<string>('');
  selectedCategoryName = signal<string>('');

  // Signals
  items = signal<InventoryItem[]>([]);
  loading = signal<boolean>(true);
  error = signal<string | null>(null);
  dashboard = signal<InventoryDashboard | null>(null);
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
  formLocalPickup = signal<boolean>(false);
  /** What buyers will see; the server applies the same rule (PLAN.md 3.5). */
  formDeliveryPreview = computed(() =>
    classifyDelivery(this.formShipping(), this.formCo2Score(), this.formLocalPickup()),
  );
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
      filtered = filtered.filter((item) => item.category === this.selectedCategory());
    }

    // Filter by search query
    if (this.searchQuery()) {
      const query = this.searchQuery().toLowerCase();
      filtered = filtered.filter((item) => (item.productName || '').toLowerCase().includes(query));
    }

    return filtered;
  });

  categories = computed(() => {
    const cats = new Set(this.items().map((item) => item.category));
    return ['all', ...Array.from(cats)];
  });

  stats = computed(() => {
    const items = this.items();
    return {
      total: items.length,
      inStock: items.filter((i) => i.stockQuantity > 0).length,
      lowStock: items.filter((i) => i.isLowStock).length,
      outOfStock: items.filter((i) => i.isOutOfStock).length,
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

      this.loadCategoryTree();
    }
  }

  loadCategoryTree(): void {
    this.categoryService
      .getRootCategories()
      .pipe(
        switchMap((roots) => {
          if (!roots || roots.length === 0) return of([]);
          const obs = roots.map((root) =>
            this.categoryService.getSubCategories(root.id).pipe(
              switchMap((subs) => {
                if (!subs || subs.length === 0) return of({ ...root, children: [] });
                const subObs = subs.map((sub) =>
                  this.categoryService.getSubCategories(sub.id).pipe(
                    map((leafs) => ({
                      ...sub,
                      children: (leafs ?? []).map((leaf) => ({ ...leaf, children: [] })),
                    })),
                    catchError(() => of({ ...sub, children: [] })),
                  ),
                );
                return forkJoin(subObs).pipe(
                  map((subsWithLeafs) => ({ ...root, children: subsWithLeafs })),
                );
              }),
              catchError(() => of({ ...root, children: [] })),
            ),
          );
          return forkJoin(obs);
        }),
      )
      .subscribe({
        next: (data) => this.categoryTree.set(data),
        error: (err: unknown) => this.toast.apiError(err, 'Categories couldn’t be loaded.'),
      });
  }

  selectCategory(root: string, sub?: string, leaf?: string, event?: Event): void {
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }
    const path = [root, sub, leaf].filter((v) => !!v).join(' > ');
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
      error: () => {
        this.error.set('Your inventory couldn’t be loaded.');
        this.loading.set(false);
      },
    });
  }

  loadDashboard(): void {
    this.inventoryService.getDashboard(this.sellerId).subscribe({
      next: (data) => {
        this.dashboard.set(data);
      },
      error: (err: unknown) => this.toast.apiError(err, 'Your stock summary couldn’t be loaded.'),
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
    this.formLocalPickup.set(false);
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
    const addPriceProblem = priceError(this.formPrice());
    if (addPriceProblem) {
      this.addError.set(addPriceProblem);
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
      localPickup: this.formLocalPickup(),
      isHandmade: this.formIsHandmade(),
      stockQuantity: this.formStockQuantity(),
      lowStockThreshold: this.formLowStockThreshold(),
      city: this.formCity(),
      region: this.formRegion(),
    };

    this.inventoryService.addProduct(this.sellerId, newItem).subscribe({
      next: () => {
        this.addLoading.set(false);
        this.showAddDialog.set(false);
        this.resetForm();
        this.toast.success('Product added. It goes live once a moderator approves it.');
        this.loadInventory();
        this.loadDashboard();
      },
      error: (err: HttpErrorResponse) => {
        this.addLoading.set(false);
        this.addError.set(err.error?.message || 'The product wasn’t added. Try again.');
      },
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
      const editPriceProblem = priceError(this.editPrice());
      if (editPriceProblem) {
        this.restockLoading.set(false);
        this.restockError.set(editPriceProblem);
        return;
      }
      const updatedItem: Partial<InventoryItem> = {
        ...item, // Preserve existing fields
        productName: this.editName(),
        price: this.editPrice(),
        description: this.editDescription(),
        imageUrl: this.editImageUrl(),
        lowStockThreshold: this.editLowStockThreshold(),
        stockQuantity: this.restockQuantity(),
        city: this.editCity(),
        region: this.editRegion(),
      };

      // Single update call handles everything including stock, preventing race conditions
      this.inventoryService.updateProduct(this.sellerId, item.productId, updatedItem).subscribe({
        next: (saved) => {
          // content edits of a live listing, and any edit after "changes requested", go back for review
          const resubmitted = saved?.status === 'PENDING' && item.status !== 'PENDING';
          this.finishStockUpdate(
            resubmitted
              ? 'Changes saved. The listing is hidden from buyers until a moderator approves it again.'
              : 'Changes saved.',
          );
        },
        error: (err: HttpErrorResponse) => {
          this.restockLoading.set(false);
          this.restockError.set(err.error?.message || 'The product wasn’t updated. Try again.');
        },
      });
    } else {
      // Simple restock (add quantity)
      if (this.restockQuantity() <= 0) {
        this.restockError.set('Please enter a valid quantity.');
        this.restockLoading.set(false);
        return;
      }
      this.inventoryService
        .restockProduct(this.sellerId, item.productId, this.restockQuantity())
        .subscribe({
          next: () => this.finishStockUpdate(),
          error: (err: HttpErrorResponse) => {
            this.restockLoading.set(false);
            this.restockError.set(err.error?.message || 'The stock wasn’t updated. Try again.');
          },
        });
    }
  }

  private finishStockUpdate(message = 'Stock updated.'): void {
    this.restockLoading.set(false);
    this.showRestockDialog.set(false);
    this.toast.success(message);
    this.loadInventory();
    this.loadDashboard();
  }

  async deleteProduct(item: InventoryItem): Promise<void> {
    const confirmed = await this.dialog.confirm({
      title: `Remove “${item.productName}”?`,
      message: 'Buyers will no longer see it. Past orders aren’t affected.',
      confirmLabel: 'Remove product',
      cancelLabel: 'Keep product',
      danger: true,
    });
    if (!confirmed) return;

    this.inventoryService.removeProduct(this.sellerId, item.productId).subscribe({
      next: () => {
        this.toast.success(`“${item.productName}” removed.`);
        this.loadInventory();
        this.loadDashboard();
      },
      error: (err: unknown) => this.toast.apiError(err, 'The product wasn’t removed. Try again.'),
    });
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
