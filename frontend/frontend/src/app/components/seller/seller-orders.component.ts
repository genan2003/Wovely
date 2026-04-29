import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { InventoryService } from '../../services/inventory.service';
import { StorageService } from '../../services/storage.service';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
    selector: 'app-seller-orders',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './seller-orders.component.html',
    styleUrls: ['./seller-orders.component.css']
})
export class SellerOrdersComponent implements OnInit {
    inventoryService = inject(InventoryService);
    storageService = inject(StorageService);

    // Signals
    orders = signal<any[]>([]);
    loading = signal<boolean>(true);
    error = signal<string | null>(null);
    
    // Status update
    updatingStatus = signal<boolean>(false);

    // Eco Label
    generatingLabel = signal<boolean>(false);
    showLabelDialog = signal<boolean>(false);
    selectedLabel = signal<string>('');
    
    // Edit order
    showEditDialog = signal<boolean>(false);
    selectedOrder = signal<any>(null);
    editLoading = signal<boolean>(false);
    editError = signal<string | null>(null);
    
    // Form fields
    editShippingAddress = signal<string>('');
    editItems = signal<any[]>([]);

    sellerId: string = '';

    ngOnInit(): void {
        const user = this.storageService.getUser();
        if (user) {
            this.sellerId = user.id;
            this.loadOrders();
        }
    }

    loadOrders(): void {
        this.loading.set(true);
        this.inventoryService.getOrdersBySeller(this.sellerId).subscribe({
            next: (response) => {
                this.orders.set(response.orders);
                this.loading.set(false);
            },
            error: (err) => {
                this.error.set('Failed to load orders.');
                this.loading.set(false);
                console.error(err);
            }
        });
    }

    canEdit(order: any): boolean {
        return order.status === 'PENDING' || order.status === 'CONFIRMED';
    }

    openEditDialog(order: any): void {
        this.selectedOrder.set(order);
        this.editShippingAddress.set(order.shippingAddress);
        // Clone items for editing
        this.editItems.set(JSON.parse(JSON.stringify(order.items)));
        this.editError.set(null);
        this.showEditDialog.set(true);
    }

    closeEditDialog(): void {
        this.showEditDialog.set(false);
        this.selectedOrder.set(null);
    }

    saveOrderChanges(): void {
        const orderId = this.selectedOrder().id;
        const updatedData = {
            shippingAddress: this.editShippingAddress(),
            items: this.editItems()
        };

        this.editLoading.set(true);
        this.inventoryService.updateOrder(orderId, updatedData).subscribe({
            next: () => {
                this.editLoading.set(false);
                this.showEditDialog.set(false);
                this.loadOrders();
            },
            error: (err: HttpErrorResponse) => {
                this.editLoading.set(false);
                this.editError.set(err.error?.error || 'Failed to update order.');
                console.error(err);
            }
        });
    }

    updateStatus(order: any, newStatus: string): void {
        if (confirm(`Are you sure you want to change status to ${newStatus}?`)) {
            this.updatingStatus.set(true);
            this.inventoryService.updateOrderStatus(order.id, newStatus).subscribe({
                next: () => {
                    this.updatingStatus.set(false);
                    this.loadOrders();
                },
                error: (err: HttpErrorResponse) => {
                    this.updatingStatus.set(false);
                    alert('Failed to update status: ' + (err.error?.error || err.message));
                }
            });
        }
    }

    generateEcoLabel(order: any): void {
        this.generatingLabel.set(true);
        this.inventoryService.generateEcoLabel(order.id).subscribe({
            next: (response) => {
                this.generatingLabel.set(false);
                this.selectedLabel.set(response.label);
                this.showLabelDialog.set(true);
                this.loadOrders(); // Refresh to show new tracking if generated
            },
            error: (err: HttpErrorResponse) => {
                this.generatingLabel.set(false);
                alert('Failed to generate eco-label: ' + (err.error?.error || err.message));
            }
        });
    }

    closeLabelDialog(): void {
        this.showLabelDialog.set(false);
        this.selectedLabel.set('');
    }

    printLabel(): void {
        // Simple mock print logic
        const printWindow = window.open('', '_blank');
        if (printWindow) {
            printWindow.document.write('<html><head><title>Eco Shipping Label</title>');
            printWindow.document.write('<style>body { font-family: monospace; white-space: pre; padding: 40px; border: 2px dashed #000; display: inline-block; }</style>');
            printWindow.document.write('</head><body>');
            printWindow.document.write(this.selectedLabel().replace(/\n/g, '<br>'));
            printWindow.document.write('</body></html>');
            printWindow.document.close();
            printWindow.print();
        }
    }

    getStatusClass(status: string): string {
        switch (status) {
            case 'PENDING': return 'badge-pending';
            case 'CONFIRMED': return 'badge-confirmed';
            case 'PROCESSING': return 'badge-processing';
            case 'SHIPPED': return 'badge-shipped';
            case 'DELIVERED': return 'badge-delivered';
            case 'COMPLETED': return 'badge-completed';
            case 'CANCELLED': return 'badge-cancelled';
            default: return 'badge-default';
        }
    }
}
