package com.wovely.wovely;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.wovely.common.error.ApiException;
import com.wovely.wovely.models.EOrderStatus;
import com.wovely.wovely.models.EWalletTransactionType;
import com.wovely.wovely.models.Order;
import com.wovely.wovely.models.WalletTransaction;
import com.wovely.wovely.services.WalletService;
import com.wovely.wovely.support.AbstractMongoIT;
import com.wovely.wovely.support.Orders;

/** PLAN.md 3.7 — mock wallet against real MongoDB: atomic debits and the escrow money flow. */
class WalletServiceIT extends AbstractMongoIT {

    @Autowired WalletService wallet;

    private final String buyer = "wb-" + UUID.randomUUID();
    private final String seller = "ws-" + UUID.randomUUID();

    private Order order(double total) {
        Order o = Orders.order("o-" + UUID.randomUUID(), buyer, seller, EOrderStatus.PENDING);
        o.setTotalCents(Math.round(total * 100));
        return o;
    }

    @Test
    void topUpAndLedger() {
        assertThat(wallet.balanceCents(buyer)).isZero();
        assertThat(wallet.topUp(buyer, new BigDecimal("12.34"))).isEqualTo(1234);
        assertThat(wallet.topUp(buyer, new BigDecimal("0.66"))).isEqualTo(1300);
        List<WalletTransaction> tx = wallet.transactions(buyer, 10);
        assertThat(tx).extracting(WalletTransaction::getAmountCents).containsExactly(66L, 1234L);
        assertThat(tx.get(0).getBalanceAfterCents()).isEqualTo(1300);
    }

    @Test
    void chargeNeedsEnoughBalance() {
        Order o = order(20.0);
        assertThatThrownBy(() -> wallet.chargeOrder(o)).isInstanceOf(ApiException.class).hasMessageContaining("Insufficient");
        assertThat(wallet.balanceCents(buyer)).isZero();
        wallet.topUp(buyer, new BigDecimal("25"));
        wallet.chargeOrder(o);
        assertThat(o.getPaidCents()).isEqualTo(2000);
        assertThat(wallet.balanceCents(buyer)).isEqualTo(500);
    }

    @Test
    void concurrentChargesNeverOverdraw() throws Exception {
        wallet.topUp(buyer, new BigDecimal("50")); // enough for exactly 5 × 10.00
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        List<Runnable> jobs = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            jobs.add(() -> {
                try {
                    start.await();
                    wallet.chargeOrder(order(10.0));
                    ok.incrementAndGet();
                } catch (Exception ignored) {
                }
            });
        }
        jobs.forEach(pool::submit);
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(ok.get()).isEqualTo(5);
        assertThat(wallet.balanceCents(buyer)).isZero();
    }

    @Test
    void completedPaysSellerOnceAndCancelRefundsBuyer() {
        wallet.topUp(buyer, new BigDecimal("100"));
        Order done = order(30.0);
        wallet.chargeOrder(done);
        wallet.releaseToSeller(done);
        wallet.releaseToSeller(done);
        assertThat(wallet.balanceCents(seller)).isEqualTo(3000);

        Order cancelled = order(20.0);
        wallet.chargeOrder(cancelled);
        wallet.refundRemaining(cancelled);
        wallet.refundRemaining(cancelled);
        assertThat(wallet.balanceCents(buyer)).isEqualTo(7000);
        assertThat(cancelled.getWalletRefundedCents()).isEqualTo(2000);
    }

    @Test
    void returnAfterPayoutIsTakenBackFromSellerAndCapped() {
        wallet.topUp(buyer, new BigDecimal("40"));
        Order o = order(40.0);
        wallet.chargeOrder(o);
        wallet.releaseToSeller(o);

        wallet.refundReturn(o, 1500L);
        assertThat(wallet.balanceCents(buyer)).isEqualTo(1500);
        assertThat(wallet.balanceCents(seller)).isEqualTo(2500);
        assertThat(o.getPayoutCents()).isEqualTo(2500);

        wallet.refundReturn(o, 99900L); // capped at what was paid
        assertThat(wallet.balanceCents(buyer)).isEqualTo(4000);
        assertThat(wallet.balanceCents(seller)).isZero();
        assertThat(wallet.transactions(seller, 10)).extracting(WalletTransaction::getType)
            .contains(EWalletTransactionType.PAYOUT, EWalletTransactionType.PAYOUT_REVERSAL);
    }

    @Test
    void sellerEditChargesOrRefundsTheDifference() {
        wallet.topUp(buyer, new BigDecimal("30"));
        Order o = order(20.0);
        wallet.chargeOrder(o);
        o.setTotalCents(2500L);
        wallet.settleEdit(o);
        assertThat(wallet.balanceCents(buyer)).isEqualTo(500);
        o.setTotalCents(4000L);
        assertThatThrownBy(() -> wallet.settleEdit(o)).hasMessageContaining("Insufficient");
        o.setTotalCents(1000L);
        wallet.settleEdit(o);
        assertThat(wallet.balanceCents(buyer)).isEqualTo(2000);
        assertThat(o.getPaidCents()).isEqualTo(1000);
    }

    @Test
    void legacyOrdersWithoutPaymentAreIgnored() {
        Order legacy = order(10.0);
        wallet.refundRemaining(legacy);
        wallet.releaseToSeller(legacy);
        assertThat(wallet.balanceCents(buyer)).isZero();
        assertThat(wallet.balanceCents(seller)).isZero();
    }
}
