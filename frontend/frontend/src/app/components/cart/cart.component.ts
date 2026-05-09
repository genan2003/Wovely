import { Component, inject, signal, OnInit, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { CartService, CartGroup } from '../../services/cart.service';
import { OrderService } from '../../services/order.service';
import { StorageService } from '../../services/storage.service';
import { FavoriteService } from '../../services/favorite.service';
import { ReturnService, ReturnRequest } from '../../services/return.service';
import { ReviewService } from '../../services/review.service';
import { PublicUserService } from '../../services/public-user.service';
import { Order, OrderStatus } from '../../models/order.model';
import { Product } from '../../models/product.model';
import { Review } from '../../models/review.model';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';

@Component({
    selector: 'app-cart',
    standalone: true,
    imports: [CommonModule, RouterLink, FormsModule],
    templateUrl: './cart.component.html',
    styleUrl: './cart.component.css'
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

    public activeTab = signal<string>('cart');
    public orders = signal<Order[]>([]);
    public loadingOrders = signal<boolean>(false);
    public isCheckingOut = signal<boolean>(false);

    // Favorites Support
    public favorites = this.favoriteService.getFavorites;
    public groupedFavorites = computed(() => {
        const groups: { [key: string]: Product[] } = {};
        this.favorites().forEach(p => {
            const sellerId = p.sellerId || 'Other';
            if (!groups[sellerId]) groups[sellerId] = [];
            groups[sellerId].push(p);
        });
        return Object.keys(groups).map(key => ({
            sellerId: key,
            sellerName: groups[key][0].sellerName || 'Shop name',
            items: groups[key]
        }));
    });

    // Returns Support
    public returns = this.returnService.getReturns;
    public eligibleOrders = signal<Order[]>([]);
    public showCreateForm = signal<boolean>(false);
    public selectedOrder = signal<Order | null>(null);
    public selectedItems = signal<any[]>([]);
    public returnReason = signal<string>('');
    public reviewedItems = signal<{[key: string]: any[]}>({});
    public sellerInfo = signal<{[key: string]: {name: string, avatar: string}}>({});

    ngOnInit(): void {
        this.loadOrders();
        this.loadEligibleOrders();
        
        // Listen for tab changes from query params
        this.route.queryParams.subscribe(params => {
            if (params['tab']) {
                this.activeTab.set(params['tab']);
            } else {
                this.activeTab.set('cart');
            }
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
                error: (err) => {
                    console.error('Failed to load orders', err);
                    this.loadingOrders.set(false);
                }
            });
            this.returnService.loadBuyerReturns(user.id);
        }
    }

    // Returns Logic
    loadEligibleOrders(): void {
        const user = this.storageService.getUser();
        if (!user) return;

        // Filter orders from the last 30 days and delivered
        const thirtyDaysAgo = new Date();
        thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);
        
        this.eligibleOrders.set(this.orders().filter(o => {
            const isRecent = new Date(o.createdAt) >= thirtyDaysAgo;
            const isDelivered = o.status === 'DELIVERED';
            const hasReturnableItems = o.items.some(item => !item.isReturned);
            return isRecent && isDelivered && hasReturnableItems;
        }));
    }

    toggleReturnItem(item: any): void {
        const current = this.selectedItems();
        const index = current.findIndex(i => i.productId === item.productId);
        if (index > -1) {
            this.selectedItems.set(current.filter(i => i.productId !== item.productId));
        } else {
            this.selectedItems.set([...current, { ...item, returnQuantity: 1 }]);
        }
    }

    isReturnItemSelected(productId: string): boolean {
        return this.selectedItems().some(i => i.productId === productId);
    }

    submitReturn(): void {
        const user = this.storageService.getUser();
        if (!user || !this.selectedOrder() || this.selectedItems().length === 0) return;

        const total = this.selectedItems().reduce((sum, i) => sum + (i.price * i.returnQuantity), 0);

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
            createdAt: new Date()
        };

        this.returnService.createReturn(returnReq).subscribe({
            next: () => {
                this.showCreateForm.set(false);
                this.selectedOrder.set(null);
                this.selectedItems.set([]);
                this.returnReason.set('');
                alert('Return request submitted!');
            },
            error: (err) => {
                console.error('Failed to submit return', err);
                const msg = err.error?.message || err.message || 'Unknown error';
                alert('Error submitting return: ' + msg);
            }
        });
    }

    getReturnStatusStep(status: string): number {
        const steps: { [key: string]: number } = {
            'PICKED_UP': 1,
            'ARRIVED': 2,
            'INSPECTION': 3,
            'REFUNDED': 4,
            'DENIED': 4
        };
        return steps[status] || 1;
    }

    isOrderReviewed(order: Order): boolean {
        return order.isReviewed || order.items.some(item => item.isReviewed);
    }

    loadOrderReviews(order: Order): void {
        if (this.reviewedItems()[order.id]) return; // Already loaded

        const requests = order.items.map(item => 
            this.reviewService.getReviewsByProductId(item.productId)
        );

        forkJoin(requests).subscribe(results => {
            const reviewsForOrder = results.map((res, index) => {
                // Find the review by current user for this product
                const user = this.storageService.getUser();
                return res.reviews.find((r: Review) => r.userId === user.id) || null;
            }).filter(r => r !== null);

            this.reviewedItems.update(prev => ({
                ...prev,
                [order.id]: reviewsForOrder
            }));
        });
    }

    confirmReceipt(orderId: string): void {
        if (confirm('Are you sure you have received all items in this order? This will move the order to your history.')) {
            this.orderService.confirmReceipt(orderId).subscribe({
                next: () => {
                    alert('Order marked as delivered! You can now review it in your History.');
                    this.loadOrders(); // Refresh lists
                    this.setTab('history');
                },
                error: (err) => {
                    console.error('Failed to confirm receipt', err);
                    alert('Error: ' + (err.error?.message || 'Unknown error'));
                }
            });
        }
    }

    cancelOrder(orderId: string): void {
        const reason = prompt('Why are you cancelling this order?');
        if (reason === null) return;

        if (confirm('Are you sure you want to cancel this order?')) {
            this.orderService.cancelOrder(orderId, reason).subscribe({
                next: () => {
                    alert('Order cancelled successfully.');
                    this.loadOrders();
                },
                error: (err: any) => {
                    console.error('Failed to cancel order', err);
                    alert('Error: ' + (err.error?.message || 'Unknown error'));
                }
            });
        }
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
            queryParamsHandling: 'merge'
        });

        if (tab === 'history') {
            this.orders().forEach(o => {
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
        const uniqueSellerIds = [...new Set(this.favorites().map(p => p.sellerId))];
        uniqueSellerIds.forEach(id => {
            if (this.sellerInfo()[id]) return;
            
            this.publicUserService.getSellerProfile(id).subscribe({
                next: (profile) => {
                    this.sellerInfo.update(prev => ({
                        ...prev,
                        [id]: {
                            name: profile.fullName || profile.username,
                            avatar: profile.workshopImageUrl || 'icons/user.png'
                        }
                    }));
                },
                error: () => {
                    this.sellerInfo.update(prev => ({
                        ...prev,
                        [id]: {
                            name: 'Artisan Shop',
                            avatar: 'icons/user.png'
                        }
                    }));
                }
            });
        });
    }

    getFilteredOrders(statuses: string[]): Order[] {
        return this.orders().filter(o => statuses.includes(o.status));
    }

    calculateGroupTotal(group: CartGroup): number {
        const itemsTotal = group.items.reduce((total: number, item: any) => total + (item.price * item.quantity), 0);
        return itemsTotal + group.deliveryCost;
    }

    checkout(group?: CartGroup) {
        const user = this.storageService.getUser();
        if (!user) {
            alert('Please login to checkout.');
            return;
        }

        let grouped: CartGroup[] = this.cartService.getGroupedItems();
        if (grouped.length === 0) return;

        // If a specific group is passed, only checkout that one.
        // Otherwise, checkout all (fallback/legacy behavior).
        if (group) {
            grouped = [group];
        }

        this.isCheckingOut.set(true);

        // Create an observable for each seller group
        const orderRequests = grouped.map(g => {
            const orderData = {
                buyerId: user.id,
                buyerName: user.username,
                sellerId: g.sellerId,
                sellerName: g.sellerName,
                items: g.items.map(i => ({
                    productId: i.id!,
                    productName: i.name,
                    quantity: Number(i.quantity),
                    price: Number(i.price),
                    imageUrl: i.imageUrl
                })),
                totalAmount: Number(this.calculateGroupTotal(g)),
                status: 'PENDING',
                shippingAddress: 'Buyer Default Address',
                createdAt: new Date(),
                updatedAt: new Date()
            };
            return this.orderService.createOrder(orderData);
        });

        forkJoin(orderRequests).subscribe({
            next: (results) => {
                alert(`Success! ${results.length} order(s) placed.`);
                
                // Remove only the successful groups from the cart
                grouped.forEach(g => {
                    this.cartService.removeItemsBySeller(g.sellerId);
                });

                this.loadOrders();
                this.isCheckingOut.set(false);
                this.setTab('shipped');
            },
            error: (err: any) => {
                console.error('Checkout failed', err);
                const errorMsg = err.error?.message || err.error?.error || err.message || 'Unknown error';
                alert('Checkout failed: ' + errorMsg);
                this.isCheckingOut.set(false);
            }
        });
    }
}
