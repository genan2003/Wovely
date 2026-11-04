import { DeliveryEmission } from './delivery-emission';

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
    /** Buyers may collect the item from the maker. */
    localPickup?: boolean;
    /** Server-computed (PLAN.md 3.5). */
    deliveryEmission?: DeliveryEmission;
    isHandmade?: boolean;
    handmade?: boolean;
    sellerName?: string;
    averageRating?: number;
    totalReviews?: number;
    stockQuantity?: number;
    lowStockThreshold?: number;
    status?: string;
}
