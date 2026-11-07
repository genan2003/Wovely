import { ChangeDetectionStrategy, Component, OnInit, signal, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { InventoryService } from '../../services/inventory.service';
import { StorageService } from '../../services/storage.service';
import { ReturnService, ReturnRequest } from '../../services/return.service';
import { HttpErrorResponse } from '@angular/common/http';
import { Order, OrderItem } from '../../models/order.model';
import { DialogService } from '../../services/dialog.service';
import { ToastService } from '../../services/toast.service';
import { OrderTimelineComponent } from '../shared/order-timeline/order-timeline.component';

@Component({
  selector: 'app-seller-orders',
  imports: [FormsModule, DecimalPipe, DatePipe, OrderTimelineComponent],
  templateUrl: './seller-orders.component.html',
  styleUrls: ['./seller-orders.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SellerOrdersComponent implements OnInit {
  inventoryService = inject(InventoryService);
  storageService = inject(StorageService);
  returnService = inject(ReturnService);
  private dialog = inject(DialogService);
  private toast = inject(ToastService);

  // Signals
  /** Orders whose status timeline is open (README Story 2: a clear timeline for every active order). */
  private expandedTimelines = signal<ReadonlySet<string>>(new Set());
  orders = signal<Order[]>([]);
  returns = signal<ReturnRequest[]>([]);
  loading = signal<boolean>(true);
  error = signal<string | null>(null);
  view = signal<'orders' | 'returns'>('orders');

  // Status update
  updatingStatus = signal<boolean>(false);

  // Eco Label
  generatingLabel = signal<boolean>(false);
  showLabelDialog = signal<boolean>(false);
  selectedLabel = signal<string>('');

  // Edit order
  showEditDialog = signal<boolean>(false);
  selectedOrder = signal<Order | null>(null);
  editLoading = signal<boolean>(false);
  editError = signal<string | null>(null);

  // Form fields
  editShippingAddress = signal<string>('');
  editItems = signal<OrderItem[]>([]);
  deliveryDays = signal<number>(3); // Default 3 days

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
        // Also load returns
        this.returnService.loadSellerReturns(this.sellerId).subscribe((rets) => {
          this.returns.set(rets);
          this.loading.set(false);
        });
      },
      error: () => {
        this.error.set('Your orders couldn’t be loaded.');
        this.loading.set(false);
      },
    });
  }

  /** The server decides (status up to PROCESSING and no shipping label yet); older responses fall back to status. */
  canEdit(order: Order): boolean {
    return order.editable ?? (order.status === 'PENDING' || order.status === 'CONFIRMED' || order.status === 'PROCESSING');
  }

  /** Sellers may cancel until the parcel ships (OrderStatusPolicy). */
  canCancel(order: Order): boolean {
    return order.status === 'PENDING' || order.status === 'CONFIRMED' || order.status === 'PROCESSING';
  }

  openEditDialog(order: Order): void {
    this.selectedOrder.set(order);
    this.editShippingAddress.set(order.shippingAddress ?? '');
    // Clone items for editing
    this.editItems.set(order.items.map((item) => ({ ...item })));
    this.editError.set(null);
    this.showEditDialog.set(true);
  }

  closeEditDialog(): void {
    this.showEditDialog.set(false);
    this.selectedOrder.set(null);
  }

  saveOrderChanges(): void {
    const orderId = this.selectedOrder()?.id;
    if (!orderId) return;
    const updatedData = {
      shippingAddress: this.editShippingAddress(),
      items: this.editItems(),
    };

    this.editLoading.set(true);
    this.inventoryService.updateOrder(orderId, updatedData).subscribe({
      next: () => {
        this.editLoading.set(false);
        this.showEditDialog.set(false);
        this.toast.success('Order updated.');
        this.loadOrders();
      },
      error: (err: HttpErrorResponse) => {
        this.editLoading.set(false);
        this.editError.set(err.error?.message || 'The order wasn’t updated. Try again.');
      },
    });
  }

  async updateStatus(order: Order, newStatus: string): Promise<void> {
    const label = newStatus.charAt(0) + newStatus.slice(1).toLowerCase();
    const title = `Mark order ${order.orderNumber} as ${label.toLowerCase()}?`;
    let days: number | undefined;
    if (newStatus === 'PROCESSING' || newStatus === 'SHIPPED') {
      const answer = await this.dialog.prompt({
        title,
        message: 'The buyer sees this estimate on their order.',
        label: 'Estimated delivery, in days',
        value: String(this.deliveryDays()),
        type: 'number',
        min: 1,
        confirmLabel: `Mark as ${label.toLowerCase()}`,
      });
      if (answer === null) return;
      const parsed = Number.parseInt(answer, 10);
      days = Number.isNaN(parsed) ? this.deliveryDays() : parsed;
    } else {
      const confirmed = await this.dialog.confirm({
        title,
        message: 'The buyer is told about the change straight away.',
        confirmLabel: `Mark as ${label.toLowerCase()}`,
      });
      if (!confirmed) return;
    }

    this.updatingStatus.set(true);
    this.inventoryService.updateOrderStatus(order.id, newStatus, undefined, days).subscribe({
      next: () => {
        this.updatingStatus.set(false);
        this.toast.success(`Order ${order.orderNumber} marked as ${label.toLowerCase()}.`);
        this.loadOrders();
      },
      error: (err: unknown) => {
        this.updatingStatus.set(false);
        this.toast.apiError(err, 'The status wasn’t changed. Try again.');
      },
    });
  }

  async cancelOrder(order: Order): Promise<void> {
    const reason = await this.dialog.prompt({
      title: `Cancel order ${order.orderNumber}?`,
      message: 'The items go back into stock and the buyer is refunded.',
      label: 'Reason, shown to the buyer',
      confirmLabel: 'Cancel order',
      cancelLabel: 'Keep order',
      danger: true,
    });
    if (reason === null) return;

    this.updatingStatus.set(true);
    this.inventoryService.updateOrderStatus(order.id, 'CANCELLED', reason).subscribe({
      next: () => {
        this.updatingStatus.set(false);
        this.toast.success(`Order ${order.orderNumber} cancelled.`);
        this.loadOrders();
      },
      error: (err: unknown) => {
        this.updatingStatus.set(false);
        this.toast.apiError(err, 'The order wasn’t cancelled. Try again.');
      },
    });
  }

  generateEcoLabel(order: Order): void {
    this.generatingLabel.set(true);
    this.inventoryService.generateEcoLabel(order.id).subscribe({
      next: (response) => {
        this.generatingLabel.set(false);
        this.selectedLabel.set(response.label);
        this.showLabelDialog.set(true);
        this.loadOrders(); // Refresh to show new tracking if generated
      },
      error: (err: unknown) => {
        this.generatingLabel.set(false);
        this.toast.apiError(err, 'The label wasn’t created. Try again.');
      },
    });
  }

  closeLabelDialog(): void {
    this.showLabelDialog.set(false);
    this.selectedLabel.set('');
  }

  /** Same rule as the server: one label per order, before it ships (PLAN.md 3.6). */
  canBuyLabel(order: { status: string; ecoShippingLabel?: string }): boolean {
    return (
      (order.status === 'CONFIRMED' || order.status === 'PROCESSING') && !order.ecoShippingLabel
    );
  }

  viewLabel(order: { ecoShippingLabel?: string }): void {
    this.selectedLabel.set(order.ecoShippingLabel ?? '');
    this.showLabelDialog.set(true);
  }

  printLabel(): void {
    const printWindow = window.open('', '_blank');
    if (!printWindow) return;
    // The label contains the buyer's shipping address: set it as text, never as HTML.
    const doc = printWindow.document;
    doc.title = 'Wovely shipping label';
    const style = doc.createElement('style');
    style.textContent =
      'body { font-family: monospace; white-space: pre; padding: 40px; } pre { border: 2px dashed var(--wv-line-strong); padding: 16px; display: inline-block; }';
    doc.head.appendChild(style);
    const pre = doc.createElement('pre');
    pre.textContent = this.selectedLabel();
    doc.body.appendChild(pre);
    printWindow.print();
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'PENDING':
        return 'badge-pending';
      case 'CONFIRMED':
        return 'badge-confirmed';
      case 'PROCESSING':
        return 'badge-processing';
      case 'SHIPPED':
        return 'badge-shipped';
      case 'DELIVERED':
        return 'badge-delivered';
      case 'COMPLETED':
        return 'badge-completed';
      case 'CANCELLED':
        return 'badge-cancelled';
      default:
        return 'badge-default';
    }
  }

  isExpanded(orderId: string): boolean {
    return this.expandedTimelines().has(orderId);
  }

  toggleTimeline(orderId: string): void {
    this.expandedTimelines.update((open) => {
      const next = new Set(open);
      if (!next.delete(orderId)) next.add(orderId);
      return next;
    });
  }
}
