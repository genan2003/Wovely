export interface Product {
    id?: string;
    name: string;
    description: string;
    price: number;
    sellerId: string;
    imageUrl: string;
    category: string;
    categoryPath?: string;
    materials?: string[];
    city?: string;
    region?: string;
    latitude?: number;
    longitude?: number;
    co2EmissionScore: string;
    shippingMethod: string;
    isHandmade?: boolean;
    handmade?: boolean;
    sellerName?: string;
    averageRating?: number;
    totalReviews?: number;
    stockQuantity?: number;
    lowStockThreshold?: number;
    status?: string;
}
