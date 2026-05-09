import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { OrderService } from '../../services/order.service';
import { ReviewService } from '../../services/review.service';
import { PublicUserService } from '../../services/public-user.service';
import { StorageService } from '../../services/storage.service';
import { Order } from '../../models/order.model';
import { forkJoin } from 'rxjs';

@Component({
    selector: 'app-review-order',
    standalone: true,
    imports: [CommonModule, RouterModule, FormsModule],
    templateUrl: './review-order.component.html',
    styleUrl: './review-order.component.css'
})
export class ReviewOrderComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private orderService = inject(OrderService);
    private reviewService = inject(ReviewService);
    private publicUserService = inject(PublicUserService);
    private storageService = inject(StorageService);

    public order = signal<Order | null>(null);
    public loading = signal<boolean>(true);
    public submitting = signal<boolean>(false);

    // Form data
    public sellerReview = {
        overall: 0,
        communication: 0,
        shipping: 0
    };

    public itemReviews = signal<any[]>([]);

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
                this.itemReviews.set(order.items.map((item: any) => ({
                    productId: item.productId,
                    productName: item.productName,
                    imageUrl: item.imageUrl,
                    rating: 0,
                    comment: ''
                })));
                this.loading.set(false);
            },
            error: () => {
                this.router.navigate(['/cart'], { queryParams: { tab: 'history' } });
                this.loading.set(false);
            }
        });
    }

    setRating(type: 'overall' | 'communication' | 'shipping', rating: number): void {
        this.sellerReview[type] = rating;
    }

    setItemRating(index: number, rating: number): void {
        const reviews = this.itemReviews();
        reviews[index].rating = rating;
        this.itemReviews.set([...reviews]);
    }

    postReviews(): void {
        const user = this.storageService.getUser();
        const order = this.order();
        if (!user || !order) return;

        this.submitting.set(true);
        
        const itemReviewRequests = this.itemReviews().map(ir => {
            return this.reviewService.createReview({
                productId: ir.productId,
                userId: user.id,
                username: user.username,
                rating: ir.rating,
                comment: ir.comment,
                photoUrls: []
            });
        });

        // Add the seller review request
        const sellerReviewRequest = this.publicUserService.postSellerReview(order.sellerId, {
            buyerId: user.id,
            buyerName: user.username,
            overallRating: this.sellerReview.overall,
            communicationRating: this.sellerReview.communication,
            shippingRating: this.sellerReview.shipping,
            comment: "" // Could add a field for this in UI if desired
        });

        forkJoin([...itemReviewRequests, sellerReviewRequest]).subscribe({
            next: () => {
                // Mark items as reviewed in the order service
                const productIds = this.itemReviews().map(ir => ir.productId);
                this.orderService.markReviewed(order.id, productIds).subscribe(() => {
                    alert('Thank you for your feedback!');
                    this.router.navigate(['/cart'], { queryParams: { tab: 'history' } });
                    this.submitting.set(false);
                });
            },
            error: (err) => {
                console.error('Review failed', err);
                alert('Failed to post reviews.');
                this.submitting.set(false);
            }
        });
    }
}
