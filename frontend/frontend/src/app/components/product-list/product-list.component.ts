import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ProductService } from '../../services/product.service';
import { ReviewService } from '../../services/review.service';
import { PublicUserService } from '../../services/public-user.service';
import { FavoriteService } from '../../services/favorite.service';
import {
  DeliveryEmission,
  DeliveryEmissionFilter,
  classifyDelivery,
} from '../../models/delivery-emission';
import { EmissionBadgeComponent } from '../shared/emission-badge/emission-badge.component';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { RatingComponent } from '../shared/rating/rating.component';
import { ImageFallbackDirective } from '../../directives/image-fallback.directive';
import { Product } from '../../models/product.model';
import { CartService } from '../../services/cart.service';
import { ToastService } from '../../services/toast.service';

/** Query params sent to GET /api/products (empty values are dropped by ProductService). */
export interface CatalogFilters {
  categoryPath: string;
  deliveryEmission: DeliveryEmissionFilter;
  localPickup: boolean;
  isHandmade: boolean;
  minPrice: number | null;
  maxPrice: number | null;
  city: string;
}

export function emptyFilters(): CatalogFilters {
  return {
    categoryPath: '',
    deliveryEmission: '',
    localPickup: false,
    isHandmade: false,
    minPrice: null,
    maxPrice: null,
    city: '',
  };
}

@Component({
  selector: 'app-product-list',
  imports: [
    FormsModule,
    RouterLink,
    EmissionBadgeComponent,
    EmptyStateComponent,
    RatingComponent,
    ImageFallbackDirective,
    DecimalPipe,
  ],
  templateUrl: './product-list.component.html',
  styleUrl: './product-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductListComponent implements OnInit {
  private productService = inject(ProductService);
  private reviewService = inject(ReviewService);
  private publicUserService = inject(PublicUserService);
  private cartService = inject(CartService);
  public favoriteService = inject(FavoriteService);

  private route = inject(ActivatedRoute);
  private toast = inject(ToastService);
  private destroyRef = inject(DestroyRef);

  public products = signal<Product[]>([]);
  public loading = signal<boolean>(true);
  public loadFailed = signal<boolean>(false);
  public filtersOpen = signal<boolean>(false);

  // Filters
  public filters: CatalogFilters = emptyFilters();

  readonly deliveryOptions: { value: DeliveryEmissionFilter; label: string; hint?: string }[] = [
    { value: '', label: 'Any delivery' },
    { value: 'LOW', label: 'Eco-friendly', hint: 'low or zero emission' },
    { value: 'ZERO', label: 'Zero-emission only', hint: 'bicycle, on foot or pickup' },
  ];

  ngOnInit(): void {
    this.route.queryParams.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.filters.categoryPath = params['categoryPath'] || '';
      this.fetchProducts();
    });
  }

  hasFilters(): boolean {
    return Object.values(this.filters).some((value) => value !== '' && value !== false && value !== null);
  }

  applyFilters(): void {
    this.fetchProducts();
  }

  clearFilters(): void {
    this.filters = emptyFilters();
    this.applyFilters();
  }

  /** Server value when present; demo products without one are classified locally. */
  emissionOf(p: Product): DeliveryEmission {
    return (
      p.deliveryEmission ?? classifyDelivery(p.shippingMethod, p.co2EmissionScore, p.localPickup)
    );
  }

  addToCart(product: Product, event: Event): void {
    event.preventDefault();
    this.cartService.addToCart(product);
    this.toast.success(`${product.name} added to your cart.`);
  }

  toggleFavorite(product: Product, event: Event): void {
    event.preventDefault();
    this.favoriteService.toggleFavorite(product);
  }

  fetchProducts(): void {
    this.loading.set(true);
    this.loadFailed.set(false);
    this.productService.getAllProducts(this.filters).subscribe({
      next: (data) => {
        const productList = data ?? []; // 204 No Content is an empty result
        this.products.set(productList);
        this.enrichProductData(productList);
        this.loading.set(false);
      },
      error: () => {
        this.products.set([]);
        this.loadFailed.set(true);
        this.loading.set(false);
      },
    });
  }

  private enrichProductData(productList: Product[]): void {
    const enrichedList = [...productList];

    productList.forEach((product, index) => {
      // Fetch Reviews
      if (product.id) {
        this.reviewService.getReviewsByProductId(product.id).subscribe({
          next: (res) => {
            enrichedList[index] = {
              ...enrichedList[index],
              totalReviews: res.totalReviews,
              averageRating: res.averageRating,
            };
            this.products.set([...enrichedList]);
          },
          error: () => {}, // Ratings are optional decoration
        });
      }

      // Fetch Seller Name
      if (product.sellerId) {
        this.publicUserService.getSellerProfile(product.sellerId).subscribe({
          next: (profile) => {
            enrichedList[index] = {
              ...enrichedList[index],
              sellerName: profile.fullName || profile.username,
            };
            this.products.set([...enrichedList]);
          },
          error: () => {
            enrichedList[index] = {
              ...enrichedList[index],
              sellerName: 'Independent maker',
            };
            this.products.set([...enrichedList]);
          },
        });
      }
    });
  }
}
