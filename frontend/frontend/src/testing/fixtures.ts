import { SellerReview } from '../app/models/seller-review.model';
import { UserCrm } from '../app/models/user-crm.model';
import { Order, OrderStatus } from '../app/models/order.model';
import { AuthUser } from '../app/models/auth.model';
import { Product } from '../app/models/product.model';

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

export function anOrder(overrides: Partial<Order> = {}): Order {
  return {
    id: 'o-1',
    orderNumber: 'ORD-1',
    buyerId: 'buyer-1',
    buyerName: 'testuser',
    sellerId: 'seller-1',
    sellerName: 'seller',
    items: [],
    totalAmount: 20,
    status: OrderStatus.PENDING,
    createdAt: new Date('2026-11-01'),
    updatedAt: new Date('2026-11-01'),
    ...overrides,
  };
}

export function anAuthUser(overrides: Partial<AuthUser> = {}): AuthUser {
  return {
    id: 'buyer-1',
    username: 'testuser',
    email: 'test@wovely.test',
    roles: ['ROLE_USER'],
    accessToken: 'token',
    ...overrides,
  };
}

export function aProduct(overrides: Partial<Product> = {}): Product {
  return {
    id: 'p-1',
    name: 'Woven Willow Basket',
    description: 'Hand-woven in Sibiu.',
    price: 42.5,
    sellerId: 'seller-1',
    imageUrl: '',
    category: 'Home',
    co2EmissionScore: 'Low',
    shippingMethod: 'Standard',
    status: 'APPROVED',
    ...overrides,
  };
}
