export interface InventoryItem {
    id?: string;
    productId: string;
    sellerId: string;
    productName: string;
    description?: string;
    imageUrl: string;
    category: string;
    price: number;
    stockQuantity: number;
    lowStockThreshold: number;
    co2EmissionScore?: string;
    shippingMethod?: string;
    isHandmade?: boolean;
    isActive?: boolean;
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
}
