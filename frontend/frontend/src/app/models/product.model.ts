export interface Product {
    id?: string;
    name: string;
    description: string;
    price: number;
    sellerId: string;
    imageUrl: string;
    category: string;
    co2EmissionScore: string;
    shippingMethod: string;
    isHandmade: boolean;
}
