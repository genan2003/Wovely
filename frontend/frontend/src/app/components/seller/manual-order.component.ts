import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { InventoryService } from '../../services/inventory.service';
import { InventoryItem } from '../../models/inventory.model';
import { ManualOrderRequest, ManualOrderItem, ManualOrderResponse } from '../../models/manual-order.model';
import { StorageService } from '../../services/storage.service';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
    selector: 'app-manual-order',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './manual-order.component.html',
    styleUrls: ['./manual-order.component.css']
})
export class ManualOrderComponent implements OnInit {
    inventoryService = inject(InventoryService);
    storageService = inject(StorageService);

    // Form state
    buyerName = signal<string>('');
    buyerEmail = signal<string>('');
    buyerPhone = signal<string>('');
    shippingAddress = signal<string>('');
    notes = signal<string>('');
    paymentMethod = signal<string>('CASH');
    orderSource = signal<string>('CRAFT_FAIR');

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
            error: (err: HttpErrorResponse) => {
                this.error.set('Failed to load products.');
                this.loading.set(false);
                console.error(err);
            }
        });
    }

    // Computed properties
    filteredProducts = computed(() => {
        return this.inventoryItems().filter(item => item.stockQuantity > 0);
    });

    cartTotal = computed(() => {
        return this.cartItems().reduce((total, item) => total + (item.price * item.quantity), 0);
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

        const product = this.inventoryItems().find(p => p.productId === productId);
        if (!product || quantity > product.stockQuantity) {
            return;
        }

        const current = this.selectedProducts();
        const newMap = new Map(current);
        newMap.set(productId, quantity);
        this.selectedProducts.set(newMap);

        // Update cart
        const cart = this.cartItems();
        const index = cart.findIndex(item => item.productId === productId);
        if (index >= 0) {
            cart[index].quantity = quantity;
            this.cartItems.set([...cart]);
        }
    }

    getSelectedQuantity(productId: string): number {
        return this.selectedProducts().get(productId) || 0;
    }

    // Cart management
    addToCart(productId: string): void {
        const product = this.inventoryItems().find(p => p.productId === productId);
        if (!product) return;

        const cart = this.cartItems();
        const existing = cart.find(item => item.productId === productId);
        
        if (existing) {
            if (existing.quantity < product.stockQuantity) {
                existing.quantity++;
                this.cartItems.set([...cart]);
            }
        } else {
            cart.push({
                productId: product.productId,
                productName: product.productName,
                quantity: 1,
                price: product.price,
                imageUrl: product.imageUrl
            });
            this.cartItems.set([...cart]);
        }
    }

    removeFromCart(productId: string): void {
        const cart = this.cartItems().filter(item => item.productId !== productId);
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
        if (!this.buyerName()) {
            this.error.set('Buyer name is required.');
            return;
        }
        if (!this.shippingAddress()) {
            this.error.set('Shipping address is required.');
            return;
        }
        if (this.cartItems().length === 0) {
            this.error.set('Please add at least one product to the order.');
            return;
        }

        this.submitting.set(true);

        const orderRequest: ManualOrderRequest = {
            sellerId: this.sellerId,
            sellerName: this.sellerName,
            buyerName: this.buyerName(),
            buyerEmail: this.buyerEmail() || undefined,
            buyerPhone: this.buyerPhone() || undefined,
            shippingAddress: this.shippingAddress(),
            items: this.cartItems(),
            notes: this.notes() || undefined,
            paymentMethod: this.paymentMethod(),
            source: this.orderSource()
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
                this.error.set('Failed to create order. Please try again.');
                console.error(err);
            }
        });
    }

    resetForm(): void {
        this.buyerName.set('');
        this.buyerEmail.set('');
        this.buyerPhone.set('');
        this.shippingAddress.set('');
        this.notes.set('');
        this.paymentMethod.set('CASH');
        this.orderSource.set('CRAFT_FAIR');
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
