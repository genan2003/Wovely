export interface Review {
    id?: string;
    productId: string;
    userId: string;
    username: string;
    rating: number;
    comment: string;
    photoUrls?: string[];
    createdAt?: Date;
}

export interface ReviewResponse {
    reviews: Review[];
    averageRating: number;
    totalReviews: number;
}
