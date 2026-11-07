import { StarInputComponent } from '../shared/star-input/star-input.component';
import { NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';

import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { OrderService } from '../../services/order.service';
import { ReviewService } from '../../services/review.service';
import { PublicUserService } from '../../services/public-user.service';
import { StorageService } from '../../services/storage.service';
import { Order } from '../../models/order.model';
import { forkJoin } from 'rxjs';
import { ToastService } from '../../services/toast.service';

interface ItemReviewDraft {
  productId: string;
  productName: string;
  imageUrl?: string;
  rating: number;
  comment: string;
}

@Component({
  selector: 'app-review-order',
  imports: [NgOptimizedImage, RouterModule, FormsModule, StarInputComponent],
  templateUrl: './review-order.component.html',
  styleUrl: './review-order.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReviewOrderComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private orderService = inject(OrderService);
  private reviewService = inject(ReviewService);
  private publicUserService = inject(PublicUserService);
  private storageService = inject(StorageService);
  private toast = inject(ToastService);

  public order = signal<Order | null>(null);
  public loading = signal<boolean>(true);
  public submitting = signal<boolean>(false);

  // Seller ratings (0 = not rated yet); the server requires 1–5 for each
  readonly overall = signal(0);
  readonly communication = signal(0);
  readonly shipping = signal(0);

  public itemReviews = signal<ItemReviewDraft[]>([]);

  /** Every rating set: posting partially would save the item reviews and then fail on the seller review. */
  readonly allRated = computed(
    () => this.overall() > 0 && this.communication() > 0 && this.shipping() > 0
      && this.itemReviews().every((r) => r.rating > 0),
  );

  ngOnInit(): void {
    const orderId = this.route.snapshot.paramMap.get('id');
    if (orderId) {
      this.loadOrder(orderId);
    } else {
      this.router.navigate(['/cart'], { queryParams: { tab: 'history' } });
    }
  }

  loadOrder(id: string): void {
    this.loading.set(true);
    this.orderService.getOrderById(id).subscribe({
      next: (order) => {
        this.order.set(order);
        this.itemReviews.set(
          order.items.map((item) => ({
            productId: item.productId,
            productName: item.productName,
            imageUrl: item.imageUrl,
            rating: 0,
            comment: '',
          })),
        );
        this.loading.set(false);
      },
      error: () => {
        this.router.navigate(['/cart'], { queryParams: { tab: 'history' } });
        this.loading.set(false);
      },
    });
  }

  setItemRating(index: number, rating: number): void {
    this.itemReviews.update((reviews) =>
      reviews.map((review, i) => (i === index ? { ...review, rating } : review)),
    );
  }

  postReviews(): void {
    const user = this.storageService.getUser();
    const order = this.order();
    if (!user || !order || !this.allRated()) return;

    this.submitting.set(true);

    const itemReviewRequests = this.itemReviews().map((ir) => {
      return this.reviewService.createReview({
        productId: ir.productId,
        userId: user.id,
        username: user.username,
        rating: ir.rating,
        comment: ir.comment,
        photoUrls: [],
      });
    });

    // Add the seller review request
    const sellerReviewRequest = this.publicUserService.postSellerReview(order.sellerId, {
      buyerId: user.id,
      buyerName: user.username,
      overallRating: this.overall(),
      communicationRating: this.communication(),
      shippingRating: this.shipping(),
      comment: '', // Could add a field for this in UI if desired
    });

    forkJoin([...itemReviewRequests, sellerReviewRequest]).subscribe({
      next: () => {
        // Mark items as reviewed in the order service
        const productIds = this.itemReviews().map((ir) => ir.productId);
        this.orderService.markReviewed(order.id, productIds).subscribe(() => {
          this.toast.success('Reviews posted. Thank you.');
          this.router.navigate(['/cart'], { queryParams: { tab: 'history' } });
          this.submitting.set(false);
        });
      },
      error: (err) => {
        this.toast.apiError(err, 'Your reviews weren’t posted. Try again.');
        this.submitting.set(false);
      },
    });
  }
}
