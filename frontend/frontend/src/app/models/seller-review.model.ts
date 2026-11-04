export interface SellerReview {
  id?: string;
  sellerId: string;
  buyerId: string;
  buyerName: string;
  overallRating: number;
  communicationRating: number;
  shippingRating: number;
  comment?: string;
  createdAt?: string;
  /** Server-computed (PLAN.md 3.3): the buyer received an order from this seller. */
  verifiedPurchase?: boolean;
}
