export interface Review {
    id?: string;
    productId: string;
    userId: string;
    username: string;
    rating: number;
    comment: string;
    photoUrls?: string[];
    createdAt?: Date;
    /** Server-computed (PLAN.md 3.3); never sent by the client. */
    verifiedPurchase?: boolean;
}

export interface ReviewResponse {
    reviews: Review[];
    averageRating: number;
    totalReviews: number;
}
