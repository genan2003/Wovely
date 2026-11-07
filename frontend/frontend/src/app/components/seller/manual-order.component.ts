import { ChangeDetectionStrategy, Component, OnInit, signal, computed, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { InventoryService } from '../../services/inventory.service';
import { InventoryItem } from '../../models/inventory.model';
import {
  ManualOrderRequest,
  ManualOrderItem,
  ManualOrderResponse,
} from '../../models/manual-order.model';
import { StorageService } from '../../services/storage.service';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'app-manual-order',
  imports: [ReactiveFormsModule, DecimalPipe],
  templateUrl: './manual-order.component.html',
  styleUrls: ['./manual-order.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ManualOrderComponent implements OnInit {
  inventoryService = inject(InventoryService);
  storageService = inject(StorageService);

  private fb = inject(FormBuilder);

  form = this.fb.nonNullable.group({
    buyerName: ['', Validators.required],
    buyerEmail: ['', Validators.email],
    buyerPhone: [''],
    shippingAddress: ['', Validators.required],
    notes: [''],
    paymentMethod: ['CASH'],
    orderSource: ['CRAFT_FAIR'],
  });

  // Cart state
  cartItems = signal<ManualOrderItem[]>([]);
  selectedProducts = signal<Map<string, number>>(new Map()); // productId -> quantity

  // Inventory state
  inventoryItems = signal<InventoryItem[]>([]);
  loading = signal<boolean>(false);
  submitting = signal<boolean>(false);
  error = signal<string | null>(null);
  successMessage = signal<string | null>(null);
  showSuccessModal = signal<boolean>(false);
  createdOrderNumber = signal<string>('');

  sellerId: string = '';
  sellerName: string = '';

  ngOnInit(): void {
    const user = this.storageService.getUser();
    if (user) {
      this.sellerId = user.id;
      this.sellerName = user.username;
      this.loadInventory();
    }
  }

  loadInventory(): void {
    this.loading.set(true);
    this.inventoryService.getInventory(this.sellerId).subscribe({
      next: (response: { items: InventoryItem[] }) => {
        this.inventoryItems.set(response.items);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Your products couldn’t be loaded.');
        this.loading.set(false);
      },
    });
  }

  // Computed properties
  filteredProducts = computed(() => {
    return this.inventoryItems();
  });

  cartTotal = computed(() => {
    return this.cartItems().reduce((total, item) => total + item.price * item.quantity, 0);
  });

  cartItemCount = computed(() => {
    return this.cartItems().reduce((count, item) => count + item.quantity, 0);
  });

  // Product selection
  toggleProductSelection(productId: string): void {
    const current = this.selectedProducts();
    const newMap = new Map(current);

    if (newMap.has(productId)) {
      newMap.delete(productId);
      this.removeFromCart(productId);
    } else {
      newMap.set(productId, 1);
      this.addToCart(productId);
    }

    this.selectedProducts.set(newMap);
  }

  isSelected(productId: string): boolean {
    return this.selectedProducts().has(productId);
  }

  updateQuantity(productId: string, quantity: number): void {
    if (quantity < 1) {
      this.removeFromCart(productId);
      return;
    }

    const product = this.inventoryItems().find((p) => p.productId === productId);
    if (!product) {
      return;
    }

    const current = this.selectedProducts();
    const newMap = new Map(current);
    newMap.set(productId, quantity);
    this.selectedProducts.set(newMap);

    // Update cart
    this.cartItems.update((cart) =>
      cart.map((item) => (item.productId === productId ? { ...item, quantity } : item)),
    );
  }

  getSelectedQuantity(productId: string): number {
    return this.selectedProducts().get(productId) || 0;
  }

  // Cart management
  addToCart(productId: string): void {
    const product = this.inventoryItems().find((p) => p.productId === productId);
    if (!product) return;

    this.cartItems.update((cart) =>
      cart.some((item) => item.productId === productId)
        ? cart.map((item) =>
            item.productId === productId ? { ...item, quantity: item.quantity + 1 } : item,
          )
        : [
            ...cart,
            {
              productId: product.productId,
              productName: product.productName,
              quantity: 1,
              price: product.price,
              imageUrl: product.imageUrl,
            },
          ],
    );
  }

  removeFromCart(productId: string): void {
    const cart = this.cartItems().filter((item) => item.productId !== productId);
    this.cartItems.set(cart);

    const current = this.selectedProducts();
    const newMap = new Map(current);
    newMap.delete(productId);
    this.selectedProducts.set(newMap);
  }

  // Form submission
  submitOrder(): void {
    this.error.set(null);
    this.successMessage.set(null);

    // Validation
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.cartItems().length === 0) {
      this.error.set('Add at least one product to the order.');
      return;
    }

    this.submitting.set(true);

    const values = this.form.getRawValue();
    const orderRequest: ManualOrderRequest = {
      sellerId: this.sellerId,
      sellerName: this.sellerName,
      buyerName: values.buyerName,
      buyerEmail: values.buyerEmail || undefined,
      buyerPhone: values.buyerPhone || undefined,
      shippingAddress: values.shippingAddress,
      items: this.cartItems(),
      notes: values.notes || undefined,
      paymentMethod: values.paymentMethod,
      source: values.orderSource,
    };

    this.inventoryService.createManualOrder(orderRequest).subscribe({
      next: (response: ManualOrderResponse) => {
        this.submitting.set(false);
        this.createdOrderNumber.set(response.orderNumber);
        this.showSuccessModal.set(true);
        this.resetForm();
        this.loadInventory(); // Reload to get updated stock
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.error.set(err.error?.message || 'The order wasn’t created. Try again.');
      },
    });
  }

  resetForm(): void {
    this.form.reset();
    this.cartItems.set([]);
    this.selectedProducts.set(new Map());
  }

  closeSuccessModal(): void {
    this.showSuccessModal.set(false);
  }

  getStockClass(item: InventoryItem): string {
    if (item.isOutOfStock) return 'out-of-stock';
    if (item.isLowStock) return 'low-stock';
    return 'in-stock';
  }
}
