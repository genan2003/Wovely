export type WalletTransactionType = 'TOP_UP' | 'PAYMENT' | 'PAYOUT' | 'REFUND' | 'PAYOUT_REVERSAL';

export interface WalletBalance {
  balanceCents: number;
  balance: number;
  currency: string;
}

export interface WalletTransaction {
  id: string;
  type: WalletTransactionType;
  /** Signed: positive = money in. */
  amountCents: number;
  balanceAfterCents: number;
  orderId?: string;
  description?: string;
  createdAt: string;
}

export const TRANSACTION_LABELS: Record<WalletTransactionType, string> = {
  TOP_UP: 'Top-up',
  PAYMENT: 'Order payment',
  PAYOUT: 'Sale payout',
  REFUND: 'Refund',
  PAYOUT_REVERSAL: 'Payout returned to buyer',
};
