export interface ManualOrderRequest {
    sellerId: string;
    sellerName: string;
    buyerName: string;
    buyerEmail?: string;
    buyerPhone?: string;
    shippingAddress: string;
    items: ManualOrderItem[];
    notes?: string;
    paymentMethod?: string;
    source?: string;
}

export interface ManualOrderItem {
    productId: string;
    productName: string;
    quantity: number;
    price: number;
    imageUrl?: string;
}

export interface ManualOrderResponse {
    message: string;
    order: {
        id: string;
        orderNumber: string;
        buyerName: string;
        sellerId: string;
        totalAmount: number;
        status: string;
        createdAt: string;
    };
    orderNumber: string;
}
