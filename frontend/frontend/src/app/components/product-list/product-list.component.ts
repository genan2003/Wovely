import { Component, OnInit, inject, signal, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { ProductService } from '../../services/product.service';
import { OrderService } from '../../services/order.service';
import { StorageService } from '../../services/storage.service';
import { ReviewService } from '../../services/review.service';
import { ChatService } from '../../services/chat.service';
import { PublicUserService } from '../../services/public-user.service';
import { FavoriteService } from '../../services/favorite.service';
import { Product } from '../../models/product.model';
import { Review } from '../../models/review.model';
import { SellerProfile } from '../../models/seller-profile.model';
import { ChatComponent } from '../chat/chat.component';

import { CartService } from '../../services/cart.service';

@Component({
    selector: 'app-product-list',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './product-list.component.html',
    styleUrl: './product-list.component.css'
})
export class ProductListComponent implements OnInit {
    private productService = inject(ProductService);
    private orderService = inject(OrderService);
    private storageService = inject(StorageService);
    private reviewService = inject(ReviewService);
    private chatService = inject(ChatService);
    private publicUserService = inject(PublicUserService);
    private cartService = inject(CartService);
    public favoriteService = inject(FavoriteService);

    private router = inject(Router);
    private route = inject(ActivatedRoute);

    public products = signal<Product[]>([]);
    public loading = signal<boolean>(true);
    public orderProcessing = signal<boolean>(false);
    public error = signal<string | null>(null);

    // Filters
    public filters = {
        categoryPath: '',
        isEco: false,
        isHandmade: false,
        minPrice: null,
        maxPrice: null,
        city: ''
    };

    // Chat
    public showChat = signal<boolean>(false);

    // Shipping
    public shippingOptions = [
        { id: 'bicycle', name: 'Bicycle Courier', co2: 0, price: 5, isGreen: true },
        { id: 'ev', name: 'Electric Van', co2: 0.2, price: 8, isGreen: true },
        { id: 'neutral', name: 'Carbon Neutral Truck', co2: 1.5, price: 12, isGreen: true }
    ];
    public selectedShipping = signal<any>(this.shippingOptions[0]);

    ngOnInit(): void {
        this.route.queryParams.subscribe(params => {
            this.filters.categoryPath = params['categoryPath'] || '';
            this.fetchProducts();
        });
    }

    applyFilters(): void {
        this.fetchProducts();
    }

    openDetails(product: Product): void {
        this.router.navigate(['/products', product.id]);
    }

    addToCart(product: Product, event: Event): void {
        event.stopPropagation(); // Prevent navigating to details
        this.cartService.addToCart(product);
        alert(`${product.name} added to cart!`);
    }

    toggleFavorite(product: Product, event: Event): void {
        event.stopPropagation();
        this.favoriteService.toggleFavorite(product);
    }

    fetchProducts(): void {
        this.loading.set(true);
        this.productService.getAllProducts(this.filters).subscribe({
            next: (data) => {
                // If data is null/undefined (e.g. 204 No Content), it's an empty result set
                const productList = data || [];
                
                // We only use mock data if the user hasn't applied any filters 
                // AND the database returned nothing (meaning DB is empty/seeding needed)
                const isSearching = Object.values(this.filters).some(v => v !== '' && v !== false && v !== null);
                
                if (productList.length === 0 && !isSearching) {
                    const mocks = this.filterMockData();
                    this.products.set(mocks);
                    this.enrichProductData(mocks);
                } else {
                    this.products.set(productList);
                    this.enrichProductData(productList);
                }
                
                this.loading.set(false);
            },
            error: (err) => {
                console.error('Failed to fetch products', err);
                // On total failure, fallback to mocks
                const mocks = this.filterMockData();
                this.products.set(mocks);
                this.enrichProductData(mocks);
                this.loading.set(false);
            }
        });
    }

    private enrichProductData(productList: Product[]): void {
        const enrichedList = [...productList];
        
        productList.forEach((product, index) => {
            // Fetch Reviews
            if (product.id) {
                this.reviewService.getReviewsByProductId(product.id).subscribe(res => {
                    enrichedList[index] = {
                        ...enrichedList[index],
                        totalReviews: res.totalReviews,
                        averageRating: res.averageRating
                    };
                    this.products.set([...enrichedList]);
                });
            }

            // Fetch Seller Name
            if (product.sellerId) {
                this.publicUserService.getSellerProfile(product.sellerId).subscribe({
                    next: (profile) => {
                        enrichedList[index] = {
                            ...enrichedList[index],
                            sellerName: profile.fullName || profile.username
                        };
                        this.products.set([...enrichedList]);
                    },
                    error: () => {
                        enrichedList[index] = {
                            ...enrichedList[index],
                            sellerName: 'Artisan'
                        };
                        this.products.set([...enrichedList]);
                    }
                });
            }
        });
    }

    private filterMockData(): Product[] {
        let mocks = this.getMockProducts();
        
        if (this.filters.categoryPath) {
            mocks = mocks.filter(p => p.categoryPath?.startsWith(this.filters.categoryPath) || p.category === this.filters.categoryPath);
        }
        if (this.filters.city) {
            mocks = mocks.filter(p => p.city?.toLowerCase().includes(this.filters.city.toLowerCase()));
        }
        if (this.filters.minPrice !== null) {
            mocks = mocks.filter(p => p.price >= this.filters.minPrice!);
        }
        if (this.filters.maxPrice !== null) {
            mocks = mocks.filter(p => p.price <= this.filters.maxPrice!);
        }
        if (this.filters.isEco) {
            mocks = mocks.filter(p => p.co2EmissionScore === 'Low');
        }
        if (this.filters.isHandmade) {
            mocks = mocks.filter(p => p.isHandmade);
        }
        
        return mocks;
    }

    private getMockProducts(): Product[] {
        return [
            {
                id: '1',
                name: 'Handcrafted Ceramic Mug',
                description: 'A beautiful, earthy speckled ceramic mug fired in a solar-powered kiln. Perfect for your morning coffee or tea, this mug features a unique thumb rest and a comfortable handle. Each piece is individually hand-thrown on a potters wheel.',
                price: 24.50,
                sellerId: 'user123',
                imageUrl: 'https://images.unsplash.com/photo-1614806687036-71d37803e7d6?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80',
                category: 'Ceramics',
                categoryPath: 'Home & Living > Ceramics > Mugs',
                city: 'Local',
                region: 'Transylvania',
                co2EmissionScore: 'Low',
                shippingMethod: 'Carbon Neutral Courier',
                handmade: true,
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
                category: 'Dresses',
                categoryPath: 'Clothing > Women > Dresses',
                city: 'Cluj-Napoca',
                region: 'Cluj',
                co2EmissionScore: 'Low',
                shippingMethod: 'Bicycle Delivery (Local)',
                handmade: true,
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
                category: 'Decor',
                categoryPath: 'Home & Living > Decor > Candles',
                city: 'Sibiu',
                region: 'Sibiu',
                co2EmissionScore: 'Low',
                shippingMethod: 'Standard Eco-Post',
                handmade: true,
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
                category: 'Tables',
                categoryPath: 'Home & Living > Furniture > Tables',
                city: 'Brasov',
                region: 'Brasov',
                co2EmissionScore: 'Medium',
                shippingMethod: 'Freight (Carbon Offset)',
                handmade: true,
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
                category: 'Bags',
                categoryPath: 'Clothing > Accessories > Bags',
                city: 'Bucharest',
                region: 'Ilfov',
                co2EmissionScore: 'Low',
                shippingMethod: 'Carbon Neutral Courier',
                handmade: true,
                stockQuantity: 25,
                lowStockThreshold: 5
            }
        ];
    }
}
