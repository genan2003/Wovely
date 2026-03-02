import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ProductService } from '../../services/product.service';
import { Product } from '../../models/product.model';

@Component({
    selector: 'app-product-list',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './product-list.component.html',
    styleUrl: './product-list.component.css'
})
export class ProductListComponent implements OnInit {
    private productService = inject(ProductService);

    public products = signal<Product[]>([]);
    public loading = signal<boolean>(true);
    public error = signal<string | null>(null);

    ngOnInit(): void {
        this.fetchProducts();
    }

    fetchProducts(): void {
        this.loading.set(true);
        this.productService.getAllProducts().subscribe({
            next: (data) => {
                // Hydrate with some dummy data if DB is empty to showcase the UI
                if (!data || data.length === 0) {
                    this.products.set(this.getMockProducts());
                } else {
                    this.products.set(data);
                }
                this.loading.set(false);
            },
            error: (err) => {
                console.error('Failed to fetch products', err);
                // Fallback to mock data for demonstration if microservice is offline
                this.products.set(this.getMockProducts());
                this.loading.set(false);
            }
        });
    }

    private getMockProducts(): Product[] {
        return [
            {
                id: '1',
                name: 'Handcrafted Ceramic Mug',
                description: 'A beautiful, earthy speckled ceramic mug fired in a solar-powered kiln.',
                price: 24.50,
                sellerId: 'user123',
                imageUrl: 'https://images.unsplash.com/photo-1614806687036-71d37803e7d6?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80',
                category: 'Home & Living',
                co2EmissionScore: 'Low',
                shippingMethod: 'Carbon Neutral Courier',
                isHandmade: true
            },
            {
                id: '2',
                name: 'Vintage Linen Dress',
                description: 'Upcycled vintage linen dress with delicate floral embroidery.',
                price: 85.00,
                sellerId: 'user456',
                imageUrl: 'https://images.unsplash.com/photo-1595777457583-95e059d581b8?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80',
                category: 'Clothing',
                co2EmissionScore: 'Low',
                shippingMethod: 'Bicycle Delivery (Local)',
                isHandmade: true
            },
            {
                id: '3',
                name: 'Organic Beeswax Candles',
                description: 'Set of 3 hand-poured pure beeswax candles. Zero toxic emissions.',
                price: 18.00,
                sellerId: 'user789',
                imageUrl: 'https://images.unsplash.com/photo-1603006905003-be475563bc59?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80',
                category: 'Home & Living',
                co2EmissionScore: 'Low',
                shippingMethod: 'Standard Eco-Post',
                isHandmade: true
            },
            {
                id: '4',
                name: 'Reclaimed Wood Coffee Table',
                description: 'Rustic coffee table crafted entirely from reclaimed barn wood.',
                price: 250.00,
                sellerId: 'user101',
                imageUrl: 'https://images.unsplash.com/photo-1533090481720-856c6e3c1fdc?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80',
                category: 'Furniture',
                co2EmissionScore: 'Medium',
                shippingMethod: 'Freight (Carbon Offset)',
                isHandmade: true
            },
            {
                id: '5',
                name: 'Crochet Tote Bag',
                description: 'Sturdy, reusable market tote made from recycled cotton yarn.',
                price: 32.00,
                sellerId: 'user202',
                imageUrl: 'https://images.unsplash.com/photo-1598532163257-ae3c6b2524b6?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80',
                category: 'Accessories',
                co2EmissionScore: 'Low',
                shippingMethod: 'Carbon Neutral Courier',
                isHandmade: true
            }
        ];
    }
}
