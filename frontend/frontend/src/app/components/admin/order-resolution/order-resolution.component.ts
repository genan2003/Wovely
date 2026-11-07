import { DecimalPipe } from '@angular/common';
import { OrderTimelineComponent } from '../../shared/order-timeline/order-timeline.component';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Observable, map } from 'rxjs';

import { FormsModule } from '@angular/forms';
import { AdminService } from '../../../services/admin.service';
import { Order, OrderStatus, OrderInterventionRequest } from '../../../models/order.model';
import { ToastService } from '../../../services/toast.service';

type InterventionAction =
  | 'UPDATE_STATUS'
  | 'FORCE_REFUND'
  | 'CANCEL_ORDER'
  | 'UPDATE_TRACKING'
  | 'MARK_DISPUTED'
  | 'RESOLVE_DISPUTE';

@Component({
  selector: 'app-order-resolution',
  imports: [DecimalPipe, FormsModule, OrderTimelineComponent],
  templateUrl: './order-resolution.component.html',
  styleUrls: ['./order-resolution.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderResolutionComponent implements OnInit {
  private adminService = inject(AdminService);
  private toast = inject(ToastService);

  orders = signal<Order[]>([]);
  filteredOrders = signal<Order[]>([]);
  selectedOrder = signal<Order | null>(null);
  searchQuery = signal('');
  searchType = signal<'all' | 'buyer' | 'seller' | 'orderNumber'>('all');
  statusFilter = signal<'all' | string>('all');
  isLoading = signal(false);
  error = signal<string | null>(null);

  showInterventionModal = signal(false);
  interventionAction = signal<InterventionAction>('UPDATE_STATUS');
  interventionReason = signal('');
  interventionNotes = signal('');
  interventionTracking = signal('');
  interventionStatus = signal<OrderStatus>(OrderStatus.CANCELLED);

  readonly OrderStatus = OrderStatus;

  ngOnInit(): void {
    this.loadAllOrders();
  }

  loadAllOrders(): void {
    this.isLoading.set(true);
    this.adminService.getAllOrders().subscribe({
      next: (data: Order[]) => {
        this.orders.set(data);
        this.filteredOrders.set(data);
        this.isLoading.set(false);
      },
      error: () => {
        this.error.set('Orders couldn’t be loaded.');
        this.isLoading.set(false);
      },
    });
  }

  searchOrders(): void {
    const query = this.searchQuery().trim();
    if (!query) {
      this.filteredOrders.set(this.orders());
      return;
    }

    this.isLoading.set(true);
    this.searchRequest(query).subscribe({
      next: (data) => {
        this.filteredOrders.set(data);
        this.isLoading.set(false);
      },
      error: (err: unknown) => {
        this.filteredOrders.set([]);
        this.isLoading.set(false);
        this.toast.apiError(err, 'The search failed. Try again.');
      },
    });
  }

  private searchRequest(query: string): Observable<Order[]> {
    switch (this.searchType()) {
      case 'orderNumber':
        return this.adminService.getOrderByOrderNumber(query).pipe(map((order) => (order ? [order] : [])));
      case 'buyer':
        return this.adminService.getOrdersByBuyerId(query);
      case 'seller':
        return this.adminService.getOrdersBySellerId(query);
      default:
        return this.adminService.searchOrders(query);
    }
  }

  filterByStatus(status: string): void {
    this.statusFilter.set(status);
    if (status === 'all') {
      this.filteredOrders.set(this.orders());
    } else {
      this.filteredOrders.set(this.orders().filter((o) => o.status === status));
    }
  }

  selectOrder(order: Order): void {
    this.selectedOrder.set(order);
  }

  closeOrderDetails(): void {
    this.selectedOrder.set(null);
  }

  openInterventionModal(action: InterventionAction): void {
    this.interventionAction.set(action);
    this.interventionReason.set('');
    this.interventionNotes.set('');
    this.interventionTracking.set('');
    this.showInterventionModal.set(true);
  }

  closeInterventionModal(): void {
    this.showInterventionModal.set(false);
    this.selectedOrder.set(null);
  }

  applyIntervention(): void {
    const order = this.selectedOrder();
    if (!order) return;

    const request: OrderInterventionRequest = {
      action: this.interventionAction(),
      reason: this.interventionReason(),
      adminNotes: this.interventionNotes(),
      trackingNumber: this.interventionTracking(),
      newStatus: this.interventionStatus(),
    };

    this.adminService.applyIntervention(order.id, request).subscribe({
      next: () => {
        this.toast.success('Intervention applied and logged.');
        this.loadAllOrders();
        this.closeInterventionModal();
      },
      error: (err: unknown) => this.toast.apiError(err, 'The intervention wasn’t applied. Try again.'),
    });
  }

  regenerateLabel(): void {
    const order = this.selectedOrder();
    if (!order) return;

    this.adminService.regenerateShippingLabel(order.id).subscribe({
      next: (updatedOrder: Order) => {
        this.loadAllOrders();
        this.selectedOrder.set(updatedOrder);
        this.toast.success(`New tracking number: ${updatedOrder.trackingNumber}`);
      },
      error: (err: unknown) => this.toast.apiError(err, 'The label wasn’t regenerated. Try again.'),
    });
  }

  getStatusClass(status: OrderStatus): string {
    const statusMap: Record<OrderStatus, string> = {
      [OrderStatus.PENDING]: 'status-pending',
      [OrderStatus.CONFIRMED]: 'status-confirmed',
      [OrderStatus.PROCESSING]: 'status-processing',
      [OrderStatus.SHIPPED]: 'status-shipped',
      [OrderStatus.DELIVERED]: 'status-delivered',
      [OrderStatus.COMPLETED]: 'status-delivered',
      [OrderStatus.CANCELLED]: 'status-cancelled',
      [OrderStatus.REFUNDED]: 'status-refunded',
      [OrderStatus.DISPUTED]: 'status-disputed',
    };
    return statusMap[status] || '';
  }

  formatDate(date: Date): string {
    return new Date(date).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }
}
