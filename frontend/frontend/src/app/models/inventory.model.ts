/** Listing review state, set by the products service (docs/diagrams/state/product-status.puml). */
export type ListingStatus = 'PENDING' | 'APPROVED' | 'NEEDS_CHANGES' | 'REJECTED' | 'ARCHIVED';

export interface InventoryItem {
    id?: string;
    productId: string;
    sellerId: string;
    productName: string;
    description?: string;
    imageUrl: string;
    category: string;
    categoryPath?: string;
    price: number;
    /** Same amount in whole cents (PLAN.md 7.5); read-only, the server derives it from `price`. */
    priceCents?: number;
    stockQuantity: number;
    lowStockThreshold: number;
    co2EmissionScore?: string;
    shippingMethod?: string;
    localPickup?: boolean;
    deliveryEmission?: 'ZERO' | 'LOW' | 'STANDARD';
    isHandmade?: boolean;
    city?: string;
    region?: string;
    isActive?: boolean;
    status?: ListingStatus;
    materials?: string[];
    isLowStock?: boolean;
    isOutOfStock?: boolean;
    lastRestockedAt?: Date;
}

export interface InventoryDashboard {
    totalProducts: number;
    inStock: number;
    lowStock: number;
    outOfStock: number;
    categoryBreakdown: { [category: string]: number };
    lowStockPreview: InventoryItem[];
}

export interface InventoryResponse {
    items: InventoryItem[];
    totalItems: number;
    lowStockCount: number;
    outOfStockCount: number;
    pendingReviewCount?: number;
    needsChangesCount?: number;
}
