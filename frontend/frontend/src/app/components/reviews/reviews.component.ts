import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { OrderService } from '../../services/order.service';
import { StorageService } from '../../services/storage.service';
import { ReviewService } from '../../services/review.service';
import { Order, OrderItem, OrderStatus } from '../../models/order.model';
import { Review } from '../../models/review.model';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-reviews',
  imports: [RouterLink, FormsModule, DatePipe],
  templateUrl: './reviews.component.html',
  styleUrl: './reviews.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReviewsComponent implements OnInit {
  private orderService = inject(OrderService);
  private storageService = inject(StorageService);
  private reviewService = inject(ReviewService);

  ordersToReview = signal<Order[]>([]);
  reviewedItems = signal<Review[]>([]);
  activeTab = signal<'to-review' | 'reviewed'>('to-review');

  // Review Modal State
  selectedProduct = signal<OrderItem | null>(null);
  rating = signal<number>(5);
  comment = signal<string>('');

  ngOnInit(): void {
    this.loadOrders();
    this.loadReviewedItems();
  }

  loadOrders(): void {
    const user = this.storageService.getUser();
    if (user && user.id) {
      this.orderService.getOrdersByBuyer(user.id).subscribe((orders) => {
        // Filter delivered orders that could be reviewed
        const toReview = orders.filter((o) => o.status === OrderStatus.DELIVERED);
        this.ordersToReview.set(toReview);
      });
    }
  }

  loadReviewedItems(): void {
    // In a real app, we'd need a ReviewService.getReviewsByUser(userId)
    // For now, we'll leave it empty or mock it if needed
  }

  setTab(tab: 'to-review' | 'reviewed'): void {
    this.activeTab.set(tab);
  }

  openReviewModal(product: OrderItem): void {
    this.selectedProduct.set(product);
    this.rating.set(5);
    this.comment.set('');
  }

  submitReview(): void {
    const user = this.storageService.getUser();
    const product = this.selectedProduct();
    if (!user || !product) return;

    const review: Review = {
      productId: product.productId,
      userId: user.id,
      username: user.username,
      rating: this.rating(),
      comment: this.comment(),
    };

    this.reviewService.createReview(review).subscribe(() => {
      this.selectedProduct.set(null);
      this.loadOrders(); // Refresh
    });
  }
}
