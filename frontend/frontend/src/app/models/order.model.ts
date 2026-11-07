import { DeliveryOptionId } from './delivery-option';
export interface OrderItem {
  productId: string;
  productName: string;
  quantity: number;
  price: number;
  /** Same amount in whole cents (PLAN.md 7.5); the server stores only cents. */
  priceCents?: number;
  color?: string;
  size?: string;
  imageUrl?: string;
  isReturned?: boolean;
  isReviewed?: boolean;
}

export interface Order {
  id: string;
  orderNumber: string;
  buyerId: string;
  buyerName: string;
  sellerId: string;
  sellerName: string;
  items: OrderItem[];
  totalAmount: number;
  /** Items + delivery in whole cents (PLAN.md 7.5). */
  totalCents?: number;
  status: OrderStatus;
  createdAt: Date;
  updatedAt: Date;
  /** Set when the order reaches DELIVERED; starts the 30-day return window. */
  deliveredAt?: string;
  /** Last moment a return can be opened (server-computed); absent before delivery. */
  returnDeadline?: string | null;
  /** Delivery chosen at checkout and its fee (included in totalAmount); absent on manual sales. */
  deliveryOption?: DeliveryOptionId | null;
  deliveryFee?: number | null;
  deliveryFeeCents?: number | null;
  /** Server-decided: items/address can still change (up to PROCESSING, before a shipping label). */
  editable?: boolean;
  shippingAddress?: string;
  trackingNumber?: string;
  refundReason?: string;
  cancellationReason?: string;
  adminNotes?: string;
  isDisputed?: boolean;
  disputeReason?: string;
  estimatedDeliveryDays?: number;
  isReviewed?: boolean;
  // "Ship with Platform" label (PLAN.md 3.6)
  ecoShippingLabel?: string;
  carrierName?: string;
  carrierMethod?: string;
  shippingDistanceKm?: number;
  estimatedCo2Kg?: number;
  labelGeneratedAt?: string;
  /** Status changes, oldest first (PLAN.md 7.1). Older orders get steps read from their dates (`inferred`). */
  timeline?: OrderTimelineEntry[];
}

/** Who moved the order; null when the step was read from an older order's dates. */
export type TimelineActor = 'BUYER' | 'SELLER' | 'ADMIN' | 'SYSTEM';

export interface OrderTimelineEntry {
  from: OrderStatus | null;
  status: OrderStatus;
  at: string;
  actor: TimelineActor | null;
  reason?: string | null;
  inferred: boolean;
}

export enum OrderStatus {
  PENDING = 'PENDING',
  CONFIRMED = 'CONFIRMED',
  PROCESSING = 'PROCESSING',
  SHIPPED = 'SHIPPED',
  DELIVERED = 'DELIVERED',
  COMPLETED = 'COMPLETED',
  CANCELLED = 'CANCELLED',
  REFUNDED = 'REFUNDED',
  DISPUTED = 'DISPUTED'
}

/** Checkout payload: the server derives buyer, seller, prices, total and status (PLAN.md 1.5). */
export interface CreateOrderRequest {
  items: { productId: string; quantity: number }[];
  addressId?: string;
  /** Required by the server, which adds its fee to the total. */
  deliveryOption: DeliveryOptionId;
}

export interface UpdateOrderRequest {
  shippingAddress?: string;
  items?: OrderItem[];
}

export interface OrderInterventionRequest {
  action: string;
  newStatus?: OrderStatus;
  reason?: string;
  adminNotes?: string;
  trackingNumber?: string;
}
