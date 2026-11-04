package com.wovely.wovely.models;

public enum EWalletTransactionType {
    /** Mock top-up (no card data is ever handled). */
    TOP_UP,
    /** Buyer pays an order at checkout; held in escrow until COMPLETED. */
    PAYMENT,
    /** Escrow released to the seller when the order is COMPLETED. */
    PAYOUT,
    /** Money back to the buyer (cancel, refund, return, cheaper edit). */
    REFUND,
    /** Taken back from a seller who was already paid out (refund after completion). */
    PAYOUT_REVERSAL
}
