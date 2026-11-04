package com.wovely.wovely.services;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.wovely.common.error.ApiException;
import com.wovely.wovely.models.EWalletTransactionType;
import com.wovely.wovely.models.Order;
import com.wovely.wovely.models.Wallet;
import com.wovely.wovely.models.WalletTransaction;
import com.wovely.wovely.repository.WalletTransactionRepository;

/**
 * Mock wallet with escrow (PLAN.md 3.7, README "wallet capabilities"). Balances are integer cents and
 * every change is one atomic {@code findAndModify $inc} (debits guarded by {@code balance >= amount}), so
 * concurrent checkouts can't overdraw. Each change writes a ledger line.
 *
 * <p>Order money flow: checkout debits the buyer ({@code PAYMENT}) and the platform holds it; COMPLETED
 * pays the seller ({@code PAYOUT}); cancel / refund / returns credit the buyer ({@code REFUND}), taken back
 * from the seller ({@code PAYOUT_REVERSAL}) if they were already paid. Like stock, wallet and order are
 * separate documents (saga-style, not one transaction).
 */
@Service
public class WalletService {

    public static final BigDecimal MAX_TOP_UP = new BigDecimal("5000");

    private final MongoTemplate mongo;
    private final WalletTransactionRepository ledger;

    public WalletService(MongoTemplate mongo, WalletTransactionRepository ledger) {
        this.mongo = mongo;
        this.ledger = ledger;
    }

    // ------------------------------------------------------------------ read

    public long balanceCents(String userId) {
        Wallet w = mongo.findById(userId, Wallet.class);
        return w == null ? 0 : w.getBalanceCents();
    }

    public List<WalletTransaction> transactions(String userId, int limit) {
        return ledger.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.max(1, Math.min(limit, 100))));
    }

    // ------------------------------------------------------------------ top-up

    public long topUp(String userId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_TOP_UP) > 0) {
            throw ApiException.badRequest("amount must be between 0.01 and " + MAX_TOP_UP);
        }
        if (amount.scale() > 2 && amount.stripTrailingZeros().scale() > 2) {
            throw ApiException.badRequest("amount can have at most 2 decimals");
        }
        long cents = amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        return credit(userId, cents, EWalletTransactionType.TOP_UP, null, "Top-up (demo, no real payment)");
    }

    // ------------------------------------------------------------------ order hooks

    /** Checkout: debit the buyer the order total; 409 when the balance is too low. */
    public void chargeOrder(Order order) {
        long cents = toCents(order.getTotalAmount());
        debit(order.getBuyerId(), cents, EWalletTransactionType.PAYMENT, order.getId(),
                "Order " + order.getOrderNumber());
        order.setPaidCents(cents);
    }

    /** Seller changed items before processing: charge or refund the difference (409 if the buyer can't pay). */
    public void settleEdit(Order order) {
        if (order.getPaidCents() == null) {
            return;
        }
        long target = toCents(order.getTotalAmount());
        long delta = target - order.getPaidCents();
        if (delta > 0) {
            debit(order.getBuyerId(), delta, EWalletTransactionType.PAYMENT, order.getId(),
                    "Order " + order.getOrderNumber() + " changed by seller");
        } else if (delta < 0) {
            credit(order.getBuyerId(), -delta, EWalletTransactionType.REFUND, order.getId(),
                    "Order " + order.getOrderNumber() + " changed by seller");
        }
        order.setPaidCents(target);
    }

    /** COMPLETED: release what is left in escrow to the seller (once). */
    public void releaseToSeller(Order order) {
        if (order.getPaidCents() == null || order.getPayoutCents() != null) {
            return;
        }
        long payout = order.getPaidCents() - nz(order.getWalletRefundedCents());
        if (payout > 0) {
            credit(order.getSellerId(), payout, EWalletTransactionType.PAYOUT, order.getId(),
                    "Payout for order " + order.getOrderNumber());
        }
        order.setPayoutCents(Math.max(0, payout));
    }

    /** CANCELLED / REFUNDED: give the buyer back everything not refunded yet. */
    public void refundRemaining(Order order) {
        if (order.getPaidCents() == null) {
            return;
        }
        refund(order, order.getPaidCents() - nz(order.getWalletRefundedCents()), "Refund for order ");
    }

    /** Return refunded: credit the returned amount (capped at what is still refundable). */
    public void refundReturn(Order order, double amount) {
        if (order.getPaidCents() == null) {
            return;
        }
        long refundable = order.getPaidCents() - nz(order.getWalletRefundedCents());
        refund(order, Math.min(toCents(amount), refundable), "Return refund for order ");
    }

    private void refund(Order order, long cents, String what) {
        if (cents <= 0) {
            return;
        }
        if (order.getPayoutCents() != null) {
            // Seller already got the money: take it back (their balance may go negative).
            long reversal = Math.min(cents, order.getPayoutCents());
            if (reversal > 0) {
                apply(order.getSellerId(), -reversal, EWalletTransactionType.PAYOUT_REVERSAL, order.getId(),
                        what + order.getOrderNumber(), false);
                order.setPayoutCents(order.getPayoutCents() - reversal);
            }
        }
        credit(order.getBuyerId(), cents, EWalletTransactionType.REFUND, order.getId(), what + order.getOrderNumber());
        order.setWalletRefundedCents(nz(order.getWalletRefundedCents()) + cents);
    }

    // ------------------------------------------------------------------ primitives

    long credit(String userId, long cents, EWalletTransactionType type, String orderId, String description) {
        return apply(userId, cents, type, orderId, description, false);
    }

    long debit(String userId, long cents, EWalletTransactionType type, String orderId, String description) {
        return apply(userId, -cents, type, orderId, description, true);
    }

    private long apply(String userId, long deltaCents, EWalletTransactionType type, String orderId, String description,
            boolean guarded) {
        Query q = new Query(where("_id").is(userId));
        if (guarded) {
            q.addCriteria(where("balanceCents").gte(-deltaCents));
        }
        Update u = new Update().inc("balanceCents", deltaCents).set("updatedAt", new Date());
        Wallet w;
        try {
            w = mongo.findAndModify(q, u, FindAndModifyOptions.options().returnNew(true).upsert(!guarded), Wallet.class);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            w = null; // guarded upsert raced; treat like insufficient funds
        }
        if (w == null) {
            throw ApiException.conflict("Insufficient wallet balance");
        }
        ledger.save(new WalletTransaction(userId, type, deltaCents, w.getBalanceCents(), orderId, description));
        return w.getBalanceCents();
    }

    public static long toCents(Double amount) {
        return amount == null ? 0 : BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValue();
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }
}
