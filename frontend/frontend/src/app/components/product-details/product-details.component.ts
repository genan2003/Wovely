import { ChangeDetectionStrategy, Component, OnInit, inject, signal, computed } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { VerifiedBadgeComponent } from '../shared/verified-badge/verified-badge.component';
import { EmissionBadgeComponent } from '../shared/emission-badge/emission-badge.component';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { RatingComponent } from '../shared/rating/rating.component';
import { ImageFallbackDirective } from '../../directives/image-fallback.directive';
import { classifyDelivery } from '../../models/delivery-emission';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { ProductService } from '../../services/product.service';
import { ReviewService } from '../../services/review.service';
import { PublicUserService } from '../../services/public-user.service';
import { AuthService } from '../../services/auth.service';
import { FavoriteService } from '../../services/favorite.service';
import { AdminService } from '../../services/admin.service';
import { Product } from '../../models/product.model';
import { Review } from '../../models/review.model';
import { SellerProfile } from '../../models/seller-profile.model';
import { ChatComponent } from '../chat/chat.component';
import { CartService } from '../../services/cart.service';
import { DialogService } from '../../services/dialog.service';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-product-details',
  imports: [
    RouterModule,
    ChatComponent,
    VerifiedBadgeComponent,
    EmissionBadgeComponent,
    EmptyStateComponent,
    RatingComponent,
    ImageFallbackDirective,
    DecimalPipe,
  ],
  templateUrl: './product-details.component.html',
  styleUrl: './product-details.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductDetailsComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private productService = inject(ProductService);
  private reviewService = inject(ReviewService);
  private publicUserService = inject(PublicUserService);
  private auth = inject(AuthService);
  private cartService = inject(CartService);
  public favoriteService = inject(FavoriteService);
  private adminService = inject(AdminService);
  private dialog = inject(DialogService);
  private toast = inject(ToastService);

  public product = signal<Product | null>(null);
  /** Server value; demo products without one are classified locally (PLAN.md 3.5). */
  public emissionLevel = computed(() => {
    const p = this.product();
    return p
      ? (p.deliveryEmission ??
          classifyDelivery(p.shippingMethod, p.co2EmissionScore, p.localPickup))
      : null;
  });
  public reviews = signal<Review[]>([]);
  public averageRating = signal<number>(0);
  public sellerProfile = signal<SellerProfile | null>(null);
  public loading = signal<boolean>(true);
  public showChat = signal<boolean>(false);
  public notFound = signal<boolean>(false);
  public loadFailed = signal<boolean>(false);

  scrollToReviews(event: Event): void {
    event.preventDefault();
    document.getElementById('reviews')?.scrollIntoView({ block: 'start' });
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadProduct(id);
    } else {
      this.router.navigate(['/products']);
    }
  }

  loadProduct(id: string): void {
    this.loading.set(true);
    this.productService.getProductById(id).subscribe({
      next: (product) => {
        this.product.set(product);
        this.loadAdditionalData(product);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        (err.status === 404 ? this.notFound : this.loadFailed).set(true);
        this.loading.set(false);
      },
    });
  }

  loadAdditionalData(product: Product): void {
    this.reviewService.getReviewsByProductId(product.id!).subscribe({
      next: (res) => {
        this.reviews.set(res.reviews || []);
        this.averageRating.set(res.averageRating ?? 0);
      },
      error: () => this.reviews.set([]),
    });

    this.publicUserService.getSellerProfile(product.sellerId).subscribe({
      next: (profile) => {
        this.sellerProfile.set(profile);
        this.product.update((p) =>
          p ? { ...p, sellerName: profile.fullName || profile.username } : null,
        );
      },
      // Without a profile the page simply leaves the maker section out
      error: () => this.sellerProfile.set(null),
    });
  }

  toggleChat(): void {
    if (!this.auth.isLoggedIn()) {
      this.goToLogin();
      return;
    }
    this.showChat.update((v) => !v);
  }

  addToCart(): void {
    const product = this.product();
    if (product) {
      this.cartService.addToCart(product);
      this.toast.success(`${product.name} added to your cart.`);
    }
  }

  toggleFavorite(): void {
    const product = this.product();
    if (product) {
      this.favoriteService.toggleFavorite(product);
    }
  }

  async reportProduct(): Promise<void> {
    const product = this.product();
    if (!product) return;
    if (!this.auth.isLoggedIn()) {
      this.goToLogin();
      return;
    }

    const reason = await this.dialog.prompt({
      title: `Report “${product.name}”`,
      message: 'A moderator will review this listing.',
      label: 'What’s wrong with it?',
      confirmLabel: 'Send report',
    });
    if (!reason) return;

    this.adminService
      .createReport({ targetType: 'PRODUCT', targetId: product.id!, reason })
      .subscribe({
        next: () => this.toast.success('Report sent. A moderator will review it.'),
        error: (err: unknown) => this.toast.apiError(err, 'The report wasn’t sent. Try again.'),
      });
  }

  /**
   * Goes straight to checkout, so the buyer picks a saved address and pays from the wallet
   * like every other order.
   */
  buyNow(): void {
    const product = this.product();
    if (!product) return;
    if (!this.auth.isLoggedIn()) {
      this.goToLogin();
      return;
    }
    if (!this.cartService.items().some((item) => item.id === product.id)) {
      this.cartService.addToCart(product);
    }
    this.router.navigate(['/cart']);
  }

  private goToLogin(): void {
    this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
  }
}
