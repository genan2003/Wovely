package com.wovely.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * An amount of RON as whole cents (PLAN.md 7.5). Every stored or computed amount in Wovely is a {@code long}
 * number of cents; decimals only exist at the edges — parsed from requests with {@link #centsOf(BigDecimal)} and
 * rendered for responses with {@link #toDecimal(Long)}. Floating point is never used for money.
 */
public record Money(long cents) implements Comparable<Money> {

    public static final Money ZERO = new Money(0);

    /** Largest single amount a request may carry: 9 999 999.99 RON. */
    public static final long MAX_CENTS = 999_999_999L;

    public static Money ofCents(long cents) {
        return new Money(cents);
    }

    /**
     * Cents of a decimal amount from a request. Rejects (never rounds) more than two decimals, negative amounts and
     * amounts above {@link #MAX_CENTS}, so a client can't sneak in a price the server would silently change.
     *
     * @throws IllegalArgumentException with a message fit for a 400 response
     */
    public static long centsOf(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("amount is required");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        BigDecimal cents = amount.movePointRight(2);
        if (cents.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("amount can have at most 2 decimals");
        }
        if (cents.compareTo(BigDecimal.valueOf(MAX_CENTS)) > 0) {
            throw new IllegalArgumentException("amount is too large");
        }
        return cents.longValueExact();
    }

    /**
     * Cents of an amount stored before 7.5 as a binary double. Uses the double's shortest decimal form
     * ({@link BigDecimal#valueOf(double)}: 0.145 → "0.145", not 0.14499…) and rounds half up — the same rule
     * the wallet has always used, so migrated orders keep the amount that was actually charged.
     */
    public static long legacyCents(Number amount) {
        if (amount == null) {
            return 0;
        }
        BigDecimal exact = amount instanceof BigDecimal b ? b : BigDecimal.valueOf(amount.doubleValue());
        return exact.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    /** The decimal form for JSON (scale 2), or {@code null} for an unknown amount. */
    public static BigDecimal toDecimal(Long cents) {
        return cents == null ? null : BigDecimal.valueOf(cents, 2);
    }

    /** {@code unit × quantity} with overflow checking. */
    public static long times(long unitCents, int quantity) {
        return Math.multiplyExact(unitCents, (long) quantity);
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(cents, other.cents));
    }

    public Money minus(Money other) {
        return new Money(Math.subtractExact(cents, other.cents));
    }

    public Money times(int quantity) {
        return new Money(times(cents, quantity));
    }

    public BigDecimal toDecimal() {
        return BigDecimal.valueOf(cents, 2);
    }

    @Override
    public int compareTo(Money o) {
        return Long.compare(cents, o.cents);
    }

    @Override
    public String toString() {
        return toDecimal().toPlainString() + " RON";
    }
}
