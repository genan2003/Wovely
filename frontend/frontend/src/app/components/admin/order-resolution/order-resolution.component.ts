import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../../services/admin.service';
import { Order, OrderStatus, OrderInterventionRequest } from '../../../models/order.model';

@Component({
  selector: 'app-order-resolution',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './order-resolution.component.html',
  styleUrls: ['./order-resolution.component.css']
})
export class OrderResolutionComponent implements OnInit {
  private adminService = inject(AdminService);

  orders = signal<Order[]>([]);
  filteredOrders = signal<Order[]>([]);
  selectedOrder = signal<Order | null>(null);
  searchQuery = signal('');
  searchType = signal<'all' | 'buyer' | 'seller' | 'orderNumber'>('all');
  statusFilter = signal<'all' | string>('all');
  isLoading = signal(false);
  error = signal<string | null>(null);

  showInterventionModal = signal(false);
  interventionAction = signal<'UPDATE_STATUS' | 'FORCE_REFUND' | 'CANCEL_ORDER' | 'UPDATE_TRACKING' | 'MARK_DISPUTED' | 'RESOLVE_DISPUTE'>('UPDATE_STATUS');
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
      error: (err: any) => {
        this.error.set('Failed to load orders');
        this.isLoading.set(false);
        console.error('Failed to load orders', err);
      }
    });
  }

  searchOrders(): void {
    const query = this.searchQuery().trim();
    if (!query) {
      this.filteredOrders.set(this.orders());
      return;
    }

    this.isLoading.set(true);
    
    if (this.searchType() === 'orderNumber') {
      this.adminService.getOrderByOrderNumber(query).subscribe({
        next: (order: Order) => {
          this.filteredOrders.set(order ? [order] : []);
          this.isLoading.set(false);
        },
        error: (err: any) => {
          this.filteredOrders.set([]);
          this.isLoading.set(false);
          console.error('Search error', err);
        }
      });
    } else if (this.searchType() === 'buyer') {
      this.adminService.getOrdersByBuyerId(query).subscribe({
        next: (data: Order[]) => {
          this.filteredOrders.set(data);
          this.isLoading.set(false);
        },
        error: (err: any) => {
          this.filteredOrders.set([]);
          this.isLoading.set(false);
          console.error('Search error', err);
        }
      });
    } else if (this.searchType() === 'seller') {
      this.adminService.getOrdersBySellerId(query).subscribe({
        next: (data: Order[]) => {
          this.filteredOrders.set(data);
          this.isLoading.set(false);
        },
        error: (err: any) => {
          this.filteredOrders.set([]);
          this.isLoading.set(false);
          console.error('Search error', err);
        }
      });
    } else {
      this.adminService.searchOrders(query).subscribe({
        next: (data: Order[]) => {
          this.filteredOrders.set(data);
          this.isLoading.set(false);
        },
        error: (err: any) => {
          this.filteredOrders.set([]);
          this.isLoading.set(false);
          console.error('Search error', err);
        }
      });
    }
  }

  filterByStatus(status: string): void {
    this.statusFilter.set(status);
    if (status === 'all') {
      this.filteredOrders.set(this.orders());
    } else {
      this.filteredOrders.set(this.orders().filter(o => o.status === status));
    }
  }

  selectOrder(order: Order): void {
    this.selectedOrder.set(order);
  }

  closeOrderDetails(): void {
    this.selectedOrder.set(null);
  }

  openInterventionModal(action: 'UPDATE_STATUS' | 'FORCE_REFUND' | 'CANCEL_ORDER' | 'UPDATE_TRACKING' | 'MARK_DISPUTED' | 'RESOLVE_DISPUTE'): void {
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
      newStatus: this.interventionStatus()
    };

    this.adminService.applyIntervention(order.id, request).subscribe({
      next: (updatedOrder: Order) => {
        this.loadAllOrders();
        this.closeInterventionModal();
      },
      error: (err: any) => {
        console.error('Intervention failed', err);
        alert('Failed to apply intervention');
      }
    });
  }

  regenerateLabel(): void {
    const order = this.selectedOrder();
    if (!order) return;

    this.adminService.regenerateShippingLabel(order.id).subscribe({
      next: (updatedOrder: Order) => {
        this.loadAllOrders();
        this.selectedOrder.set(updatedOrder);
        alert(`New tracking number generated: ${updatedOrder.trackingNumber}`);
      },
      error: (err: any) => {
        console.error('Label regeneration failed', err);
        alert('Failed to regenerate shipping label');
      }
    });
  }

  getStatusClass(status: OrderStatus): string {
    const statusMap: Record<OrderStatus, string> = {
      [OrderStatus.PENDING]: 'status-pending',
      [OrderStatus.CONFIRMED]: 'status-confirmed',
      [OrderStatus.PROCESSING]: 'status-processing',
      [OrderStatus.SHIPPED]: 'status-shipped',
      [OrderStatus.DELIVERED]: 'status-delivered',
      [OrderStatus.CANCELLED]: 'status-cancelled',
      [OrderStatus.REFUNDED]: 'status-refunded',
      [OrderStatus.DISPUTED]: 'status-disputed'
    };
    return statusMap[status] || '';
  }

  formatDate(date: Date): string {
    return new Date(date).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }
}
