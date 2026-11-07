import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal, OnInit, computed } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe, DecimalPipe, TitleCasePipe, SlicePipe, NgOptimizedImage } from '@angular/common';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { CartService, CartGroup } from '../../services/cart.service';
import { OrderService } from '../../services/order.service';
import { StorageService } from '../../services/storage.service';
import { FavoriteService } from '../../services/favorite.service';
import { ReturnItem, ReturnService, ReturnRequest } from '../../services/return.service';
import { ReviewService } from '../../services/review.service';
import { PublicUserService } from '../../services/public-user.service';
import { Order, OrderItem } from '../../models/order.model';
import { Product } from '../../models/product.model';
import { Review } from '../../models/review.model';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { AddressService } from '../../services/address.service';
import { WalletService } from '../../services/wallet.service';
import { Address, formatAddress } from '../../models/address.model';
import { DialogService } from '../../services/dialog.service';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { returnableOrders } from '../../models/order-returns';
import { DeliveryOptionService } from '../../services/delivery-option.service';
import { ToastService } from '../../services/toast.service';
import { OrderTimelineComponent } from '../shared/order-timeline/order-timeline.component';

@Component({
  selector: 'app-cart',
  imports: [NgOptimizedImage, RouterLink, FormsModule, DatePipe, DecimalPipe, SlicePipe, TitleCasePipe, EmptyStateComponent, OrderTimelineComponent],
  templateUrl: './cart.component.html',
  styleUrl: './cart.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CartComponent implements OnInit {
  public cartService = inject(CartService);
  private orderService = inject(OrderService);
  private storageService = inject(StorageService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private favoriteService = inject(FavoriteService);
  private returnService = inject(ReturnService);
  private reviewService = inject(ReviewService);
  private publicUserService = inject(PublicUserService);
  private addressService = inject(AddressService);
  private dialog = inject(DialogService);
  private toast = inject(ToastService);
  readonly delivery = inject(DeliveryOptionService);
  private destroyRef = inject(DestroyRef);

  /** Checkout destination (address book); the default entry is preselected. */
  public addresses = signal<Address[]>([]);
  public addressesLoaded = signal(false);
  public selectedAddressId = signal<string | null>(null);
  public readonly formatAddress = formatAddress;
  public wallet = inject(WalletService);

  public readonly tabs = [
    { id: 'cart', label: 'Cart' },
    { id: 'shipped', label: 'On the way' },
    { id: 'history', label: 'History' },
    { id: 'returns', label: 'Returns' },
    { id: 'favourites', label: 'Favourites' },
  ];
  public activeTab = signal<string>('cart');
  public title = computed(() => this.tabs.find((tab) => tab.id === this.activeTab())?.label ?? 'Cart');
  public orders = signal<Order[]>([]);
  public loadingOrders = signal<boolean>(false);
  public isCheckingOut = signal<boolean>(false);

  // Favorites Support
  public favorites = this.favoriteService.getFavorites;
  public groupedFavorites = computed(() => {
    const groups: { [key: string]: Product[] } = {};
    this.favorites().forEach((p) => {
      const sellerId = p.sellerId || 'Other';
      if (!groups[sellerId]) groups[sellerId] = [];
      groups[sellerId].push(p);
    });
    return Object.keys(groups).map((key) => ({
      sellerId: key,
      sellerName: groups[key][0].sellerName || 'Shop name',
      items: groups[key],
    }));
  });

  // Returns Support
  public returns = this.returnService.getReturns;
  public eligibleOrders = signal<Order[]>([]);
  public showCreateForm = signal<boolean>(false);
  public selectedOrder = signal<Order | null>(null);
  public selectedItems = signal<ReturnItem[]>([]);
  public returnReason = signal<string>('');
  public reviewedItems = signal<{ [key: string]: Review[] }>({});
  public sellerInfo = signal<{ [key: string]: { name: string; avatar: string } }>({});

  ngOnInit(): void {
    this.delivery.load();
    this.loadAddresses();
    this.loadOrders();
    this.loadEligibleOrders();

    // Listen for tab changes from query params
    this.route.queryParams.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      if (params['tab']) {
        this.activeTab.set(params['tab']);
      } else {
        this.activeTab.set('cart');
      }
    });
  }

  /** What the server debits from the wallet at checkout: the items, at server prices (PLAN.md 3.7). */
  itemsCents(group: CartGroup): number {
    return Math.round(group.items.reduce((t, i) => t + i.price * i.quantity, 0) * 100);
  }

  /** What checkout will debit: items plus the server-priced delivery fee. */
  chargedNowCents(group: CartGroup): number {
    return this.itemsCents(group) + Math.round((group.selectedShipping?.price ?? 0) * 100);
  }

  canAfford(group: CartGroup): boolean {
    const balance = this.wallet.balanceCents();
    return balance !== null && balance >= this.chargedNowCents(group);
  }

  loadAddresses(): void {
    if (!this.storageService.getUser()) {
      this.addressesLoaded.set(true);
      return;
    }
    this.wallet.refresh().subscribe({ error: () => {} });
    this.addressService.list().subscribe({
      next: (list) => {
        this.addresses.set(list ?? []);
        const preferred = list?.find((a) => a.default) ?? list?.[0];
        this.selectedAddressId.set(preferred?.id ?? null);
        this.addressesLoaded.set(true);
      },
      error: () => this.addressesLoaded.set(true),
    });
  }

  loadOrders(): void {
    const user = this.storageService.getUser();
    if (user && user.id) {
      this.loadingOrders.set(true);
      this.orderService.getOrdersByBuyer(user.id).subscribe({
        next: (data) => {
          this.orders.set(data);
          this.loadEligibleOrders(); // Re-sync eligibility
          this.loadingOrders.set(false);
        },
        error: (err: unknown) => {
          this.toast.apiError(err, 'Your orders couldn’t be loaded.');
          this.loadingOrders.set(false);
        },
      });
      this.returnService.loadBuyerReturns(user.id);
    }
  }

  // Returns Logic
  loadEligibleOrders(): void {
    const user = this.storageService.getUser();
    if (!user) return;

    // The server owns the 30-day window (returnDeadline, from the delivery date) and enforces it
    this.eligibleOrders.set(returnableOrders(this.orders()));
  }

  toggleReturnItem(item: OrderItem): void {
    const current = this.selectedItems();
    const index = current.findIndex((i) => i.productId === item.productId);
    if (index > -1) {
      this.selectedItems.set(current.filter((i) => i.productId !== item.productId));
    } else {
      this.selectedItems.set([...current, { ...item, returnQuantity: 1 }]);
    }
  }

  isReturnItemSelected(productId: string): boolean {
    return this.selectedItems().some((i) => i.productId === productId);
  }

  submitReturn(): void {
    const user = this.storageService.getUser();
    if (!user || !this.selectedOrder() || this.selectedItems().length === 0) return;

    const total = this.selectedItems().reduce((sum, i) => sum + i.price * i.returnQuantity, 0);

    const returnReq: ReturnRequest = {
      orderId: this.selectedOrder()!.id,
      orderNumber: this.selectedOrder()!.orderNumber,
      buyerId: user.id,
      sellerId: this.selectedOrder()!.sellerId,
      sellerName: this.selectedOrder()!.sellerName,
      items: this.selectedItems(),
      reason: this.returnReason(),
      totalAmount: total,
      status: 'PICKED_UP',
      createdAt: new Date(),
    };

    this.returnService.createReturn(returnReq).subscribe({
      next: () => {
        this.showCreateForm.set(false);
        this.selectedOrder.set(null);
        this.selectedItems.set([]);
        this.returnReason.set('');
        this.toast.success('Return requested.');
      },
      error: (err: unknown) => this.toast.apiError(err, 'The return wasn’t requested. Try again.'),
    });
  }

  getReturnStatusStep(status: string): number {
    const steps: { [key: string]: number } = {
      PICKED_UP: 1,
      ARRIVED: 2,
      INSPECTION: 3,
      REFUNDED: 4,
      DENIED: 4,
    };
    return steps[status] || 1;
  }

  isOrderReviewed(order: Order): boolean {
    return order.isReviewed || order.items.some((item) => item.isReviewed);
  }

  loadOrderReviews(order: Order): void {
    if (this.reviewedItems()[order.id]) return; // Already loaded

    const requests = order.items.map((item) =>
      this.reviewService.getReviewsByProductId(item.productId),
    );

    forkJoin(requests).subscribe((results) => {
      const reviewsForOrder = results
        .map((res) => res.reviews.find((r) => r.userId === this.storageService.getUser()?.id))
        .filter((r): r is Review => !!r);

      this.reviewedItems.update((prev) => ({
        ...prev,
        [order.id]: reviewsForOrder,
      }));
    });
  }

  async confirmReceipt(orderId: string): Promise<void> {
    const confirmed = await this.dialog.confirm({
      title: 'Confirm you received everything?',
      message: 'The order moves to your history and you can review it there.',
      confirmLabel: 'Confirm receipt',
    });
    if (!confirmed) return;

    this.orderService.confirmReceipt(orderId).subscribe({
      next: () => {
        this.toast.success('Receipt confirmed. You can review this order in your history.');
        this.loadOrders();
        this.setTab('history');
      },
      error: (err: unknown) => this.toast.apiError(err, 'Receipt wasn’t confirmed. Try again.'),
    });
  }

  async cancelOrder(orderId: string): Promise<void> {
    const reason = await this.dialog.prompt({
      title: 'Cancel this order?',
      message: 'Your wallet is refunded straight away.',
      label: 'Why are you cancelling?',
      confirmLabel: 'Cancel order',
      cancelLabel: 'Keep order',
      danger: true,
    });
    if (reason === null) return;

    this.orderService.cancelOrder(orderId, reason).subscribe({
      next: () => {
        this.toast.success('Order cancelled.');
        this.loadOrders();
      },
      error: (err: unknown) => this.toast.apiError(err, 'The order wasn’t cancelled. Try again.'),
    });
  }

  // Favorites Logic
  removeFavorite(productId: string) {
    this.favoriteService.removeFavorite(productId);
  }

  setTab(tab: string, event?: Event) {
    if (event) event.preventDefault();
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab: tab },
      queryParamsHandling: 'merge',
    });

    if (tab === 'history') {
      this.orders().forEach((o) => {
        if (o.status === 'DELIVERED' && this.isOrderReviewed(o)) {
          this.loadOrderReviews(o);
        }
      });
    }

    if (tab === 'favourites') {
      this.loadFavoritesMetadata();
    }
  }

  loadFavoritesMetadata(): void {
    const uniqueSellerIds = [...new Set(this.favorites().map((p) => p.sellerId))];
    uniqueSellerIds.forEach((id) => {
      if (this.sellerInfo()[id]) return;

      this.publicUserService.getSellerProfile(id).subscribe({
        next: (profile) => {
          this.sellerInfo.update((prev) => ({
            ...prev,
            [id]: {
              name: profile.fullName || profile.username,
              avatar: profile.workshopImageUrl || 'avatar.svg',
            },
          }));
        },
        error: () => {
          this.sellerInfo.update((prev) => ({
            ...prev,
            [id]: {
              name: 'Artisan Shop',
              avatar: 'avatar.svg',
            },
          }));
        },
      });
    });
  }

  getFilteredOrders(statuses: string[]): Order[] {
    return this.orders().filter((o) => statuses.includes(o.status));
  }

  calculateGroupTotal(group: CartGroup): number {
    return this.chargedNowCents(group) / 100;
  }

  checkout(group?: CartGroup) {
    const user = this.storageService.getUser();
    if (!user) {
      this.router.navigate(['/login'], { queryParams: { returnUrl: '/cart' } });
      return;
    }
    if (!this.selectedAddressId() || (group && (!this.canAfford(group) || !group.selectedShipping))) {
      return; // the template explains what is missing
    }
    // Drop items that were archived/unapproved since they were added (PLAN.md 2.8)
    this.cartService.pruneUnavailable().subscribe((removed) => {
      if (removed.length > 0) {
        this.toast.info(`No longer available, removed from your cart: ${removed.map((r) => r.name).join(', ')}`);
      }
      const fresh = group
        ? this.cartService.getGroupedItems().find((g) => g.sellerId === group.sellerId)
        : undefined;
      if (group && !fresh) {
        return;
      }
      this.placeOrders(fresh);
    });
  }

  private placeOrders(group?: CartGroup) {
    let grouped: CartGroup[] = this.cartService.getGroupedItems();
    if (grouped.length === 0) return;

    // If a specific group is passed, only checkout that one.
    // Otherwise, checkout all (fallback/legacy behavior).
    if (group) {
      grouped = [group];
    }
    grouped = grouped.filter((g) => !!g.selectedShipping);
    if (grouped.length === 0) return;

    this.isCheckingOut.set(true);

    // Create an observable for each seller group
    const orderRequests = grouped.map((g) => {
      // Only ids and quantities are sent: the server derives buyer, seller, prices,
      // total and status itself (PLAN.md 1.5).
      const orderData = {
        items: g.items.map((i) => ({
          productId: i.id!,
          quantity: Number(i.quantity),
        })),
        addressId: this.selectedAddressId() ?? undefined,
        deliveryOption: g.selectedShipping!.id,
      };
      return this.orderService.createOrder(orderData);
    });

    forkJoin(orderRequests).subscribe({
      next: (results) => {
        this.toast.success(results.length === 1 ? 'Order placed.' : `${results.length} orders placed.`);

        // Remove only the successful groups from the cart
        grouped.forEach((g) => {
          this.cartService.removeItemsBySeller(g.sellerId);
        });

        this.loadOrders();
        this.isCheckingOut.set(false);
        this.setTab('shipped');
      },
      error: (err: unknown) => {
        this.toast.apiError(err, 'Your order wasn’t placed. Nothing was charged. Try again.');
        this.isCheckingOut.set(false);
      },
    });
  }
}
