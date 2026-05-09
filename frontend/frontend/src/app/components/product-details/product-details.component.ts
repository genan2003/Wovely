import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { ProductService } from '../../services/product.service';
import { ReviewService } from '../../services/review.service';
import { PublicUserService } from '../../services/public-user.service';
import { StorageService } from '../../services/storage.service';
import { OrderService } from '../../services/order.service';
import { FavoriteService } from '../../services/favorite.service';
import { AdminService } from '../../services/admin.service';
import { Product } from '../../models/product.model';
import { Review } from '../../models/review.model';
import { SellerProfile } from '../../models/seller-profile.model';
import { ChatComponent } from '../chat/chat.component';
import { CartService } from '../../services/cart.service';

@Component({
    selector: 'app-product-details',
    standalone: true,
    imports: [CommonModule, RouterModule, ChatComponent],
    templateUrl: './product-details.component.html',
    styleUrl: './product-details.component.css'
})
export class ProductDetailsComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private productService = inject(ProductService);
    private reviewService = inject(ReviewService);
    private publicUserService = inject(PublicUserService);
    private storageService = inject(StorageService);
    private orderService = inject(OrderService);
    private cartService = inject(CartService);
    public favoriteService = inject(FavoriteService);
    private adminService = inject(AdminService);

    public product = signal<Product | null>(null);
    public safeProduct = computed(() => this.product() || ({} as Product));
    public reviews = signal<Review[]>([]);
    public averageRating = signal<number>(0);
    public sellerProfile = signal<SellerProfile | null>(null);
    public loading = signal<boolean>(true);
    public showChat = signal<boolean>(false);
    public orderProcessing = signal<boolean>(false);

    scrollToReviews(event: Event): void {
        event.preventDefault();
        const element = document.getElementById('reviews');
        if (element) {
            element.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    }

    public shippingOptions = [
        { id: 'bicycle', name: 'Bicycle Courier', co2: 0, price: 5, isGreen: true },
        { id: 'ev', name: 'Electric Van', co2: 0.2, price: 8, isGreen: true },
        { id: 'neutral', name: 'Carbon Neutral Truck', co2: 1.5, price: 12, isGreen: true }
    ];
    public selectedShipping = signal<any>(this.shippingOptions[0]);

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
            error: () => {
                // Fallback to mock for demonstration
                const mock = this.getMockProducts().find(p => p.id === id);
                if (mock) {
                    this.product.set(mock);
                    this.loadAdditionalData(mock);
                } else {
                    this.router.navigate(['/products']);
                }
                this.loading.set(false);
            }
        });
    }

    loadAdditionalData(product: Product): void {
        this.reviewService.getReviewsByProductId(product.id!).subscribe(res => {
            this.reviews.set(res.reviews || []);
            // Only fallback to 4.8 if the averageRating is truly missing or null, not if it's 0
            this.averageRating.set(res.averageRating !== undefined && res.averageRating !== null ? res.averageRating : 4.8);
        });

        this.publicUserService.getSellerProfile(product.sellerId).subscribe({
            next: (profile) => {
                this.sellerProfile.set(profile);
                this.product.update(p => p ? ({ ...p, sellerName: profile.fullName || profile.username }) : null);
            },
            error: (err: any) => {
                console.warn('Could not load seller profile:', err);
                // We keep sellerProfile as null, and the UI handles it gracefully
            }
        });

        const user = this.storageService.getUser();
        if (user && user.city === product.city) {
            this.selectedShipping.set(this.shippingOptions[0]);
        }
    }

    toggleChat(): void {
        if (!this.storageService.isLoggedIn()) {
            alert('Please login to chat.');
            return;
        }
        this.showChat.update(v => !v);
    }

    addToCart(): void {
        const product = this.product();
        if (product) {
            this.cartService.addToCart(product, {
                name: this.selectedShipping().name,
                price: this.selectedShipping().price
            });
            alert(`${product.name} added to cart!`);
        }
    }

    toggleFavorite(): void {
        const product = this.product();
        if (product) {
            this.favoriteService.toggleFavorite(product);
        }
    }

    reportProduct(): void {
        const product = this.product();
        if (!product) return;

        const reason = prompt('Please describe why you are reporting this listing:');
        if (!reason) return;

        const user = this.storageService.getUser();
        this.adminService.createReport({
            reporterId: user ? user.id : 'GUEST',
            reporterName: user ? (user.fullName || user.username) : 'Guest User',
            targetType: 'PRODUCT',
            targetId: product.id,
            targetName: product.name,
            reason: reason
        }).subscribe({
            next: () => alert('Thank you. The listing has been flagged for admin review.'),
            error: (err: any) => alert('Failed to submit report: ' + (err.error?.message || err.message))
        });
    }

    buyNow(): void {
        const product = this.product();
        if (!product) return;
        
        if (!this.storageService.isLoggedIn()) {
            alert('Please login to purchase.');
            return;
        }

        const user = this.storageService.getUser();
        const order = {
            buyerId: user.id,
            buyerName: user.fullName || user.username,
            sellerId: product.sellerId,
            sellerName: this.sellerProfile()?.fullName || this.sellerProfile()?.username || 'Seller',
            totalAmount: product.price + this.selectedShipping().price,
            status: 'PENDING',
            items: [{
                productId: product.id,
                productName: product.name,
                quantity: 1,
                price: product.price,
                imageUrl: product.imageUrl
            }],
            shippingAddress: 'Buyer Default Address',
            createdAt: new Date(),
            updatedAt: new Date()
        };

        this.orderProcessing.set(true);
        this.orderService.createOrder(order).subscribe({
            next: (res) => {
                alert(`Order successful! Order Number: ${res.orderNumber}`);
                this.orderProcessing.set(false);
            },
            error: (err: any) => {
                console.error('Order failed', err);
                alert('Checkout failed.');
                this.orderProcessing.set(false);
            }
        });
    }

    private getMockProducts(): Product[] {
        return [
            {
                id: '69f66f715f84cb31f6432ba6',
                name: 'Handcrafted Ceramic Mug',
                description: 'A beautiful, earthy speckled ceramic mug fired in a solar-powered kiln. Perfect for your morning coffee or tea, this mug features a unique thumb rest and a comfortable handle. Each piece is individually hand-thrown on a potters wheel.',
                price: 24.50,
                sellerId: '69f6015a06de5ea99ce5f21e',
                imageUrl: 'https://images.unsplash.com/photo-1614806687036-71d37803e7d6?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80',
                category: 'Home & Living',
                categoryPath: 'Home & Living > Ceramics',
                city: 'Local',
                region: 'Transylvania',
                co2EmissionScore: 'Low',
                shippingMethod: 'Carbon Neutral Courier',
                isHandmade: true,
                stockQuantity: 15,
                lowStockThreshold: 5
            },
            {
                id: '2',
                name: 'Vintage Linen Dress',
                description: 'Upcycled vintage linen dress with delicate floral embroidery. This sustainable fashion piece combines classic style with eco-conscious materials. Breathable and comfortable for summer days.',
                price: 85.00,
                sellerId: 'user456',
                imageUrl: 'https://images.unsplash.com/photo-1595777457583-95e059d581b8?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80',
                category: 'Clothing',
                categoryPath: 'Clothing > Dresses',
                city: 'Cluj-Napoca',
                region: 'Cluj',
                co2EmissionScore: 'Low',
                shippingMethod: 'Bicycle Delivery (Local)',
                isHandmade: true,
                stockQuantity: 3,
                lowStockThreshold: 5
            },
            {
                id: '3',
                name: 'Organic Beeswax Candles',
                description: 'Set of 3 hand-poured pure beeswax candles. Zero toxic emissions and a light honey scent. Long-burning and environmentally friendly alternative to paraffin candles.',
                price: 18.00,
                sellerId: 'user789',
                imageUrl: 'https://images.unsplash.com/photo-1603006905003-be475563bc59?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80',
                category: 'Home & Living',
                categoryPath: 'Home & Living > Decor',
                city: 'Sibiu',
                region: 'Sibiu',
                co2EmissionScore: 'Low',
                shippingMethod: 'Standard Eco-Post',
                isHandmade: true,
                stockQuantity: 0,
                lowStockThreshold: 5
            },
            {
                id: '4',
                name: 'Reclaimed Wood Coffee Table',
                description: 'Rustic coffee table crafted entirely from reclaimed barn wood. Solid construction with a story to tell. Each table features unique wood grain and historical character.',
                price: 250.00,
                sellerId: 'user101',
                imageUrl: 'https://images.unsplash.com/photo-1533090481720-856c6e3c1fdc?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80',
                category: 'Furniture',
                categoryPath: 'Furniture > Tables',
                city: 'Brasov',
                region: 'Brasov',
                co2EmissionScore: 'Medium',
                shippingMethod: 'Freight (Carbon Offset)',
                isHandmade: true,
                stockQuantity: 1,
                lowStockThreshold: 5
            },
            {
                id: '5',
                name: 'Crochet Tote Bag',
                description: 'Sturdy, reusable market tote made from recycled cotton yarn. Strong enough for your grocery runs and stylish enough for a day at the beach. Lightweight and foldable.',
                price: 32.00,
                sellerId: 'user202',
                imageUrl: 'https://images.unsplash.com/photo-1598532163257-ae3c6b2524b6?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80',
                category: 'Accessories',
                categoryPath: 'Accessories > Bags',
                city: 'Bucharest',
                region: 'Ilfov',
                co2EmissionScore: 'Low',
                shippingMethod: 'Carbon Neutral Courier',
                isHandmade: true,
                stockQuantity: 25,
                lowStockThreshold: 5
            }
        ];
    }
}
