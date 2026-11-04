import { SellerReview } from '../app/models/seller-review.model';
import { UserCrm } from '../app/models/user-crm.model';

/** Builders for test data. Override only the fields a test cares about. */
export function aUserCrm(overrides: Partial<UserCrm> = {}): UserCrm {
  return {
    id: 'u-1',
    username: 'artisan',
    fullName: 'Ana Artisan',
    email: 'ana@wovely.test',
    roles: ['ROLE_SELLER'],
    strikes: 0,
    accountStatus: 'ACTIVE',
    ...overrides,
  };
}

export function aSellerReview(overrides: Partial<SellerReview> = {}): SellerReview {
  return {
    id: 'sr-1',
    sellerId: 'seller-1',
    buyerId: 'buyer-1',
    buyerName: 'testuser',
    overallRating: 5,
    communicationRating: 5,
    shippingRating: 4,
    comment: 'Lovely work',
    verifiedPurchase: false,
    ...overrides,
  };
}
