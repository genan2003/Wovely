package com.wovely.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** PLAN.md 7.5: amounts are whole cents; decimals are parsed strictly and legacy doubles converted predictably. */
class MoneyTest {

    @ParameterizedTest
    @CsvSource({ "0, 0", "0.01, 1", "42.5, 4250", "42.50, 4250", "42.500, 4250", "1000, 100000", "9999999.99, 999999999" })
    void parsesRequestAmountsExactly(String amount, long cents) {
        assertThat(Money.centsOf(new BigDecimal(amount))).isEqualTo(cents);
    }

    @ParameterizedTest
    @CsvSource({ "42.505, at most 2 decimals", "0.001, at most 2 decimals", "-1, must not be negative",
            "10000000.00, too large" })
    void rejectsAmountsItWouldHaveToChange(String amount, String message) {
        assertThatThrownBy(() -> Money.centsOf(new BigDecimal(amount)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }

    @Test
    void nullAmountIsRejected() {
        assertThatThrownBy(() -> Money.centsOf(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({ "42.5, 4250", "0.1, 10", "0.145, 15", "1.005, 101", "2.675, 268", "19.99, 1999", "0.285, 29" })
    void legacyDoublesUseTheirShortestDecimalFormAndRoundHalfUp(double legacy, long cents) {
        // 1.005, 2.675 and 0.285 are just below .xx5 in binary; naive Math.round(v * 100) gives 100, 267, 28
        assertThat(Money.legacyCents(legacy)).isEqualTo(cents);
    }

    @Test
    void legacyIntegersAndNullsAreHandled() {
        assertThat(Money.legacyCents(15)).isEqualTo(1500);
        assertThat(Money.legacyCents(null)).isZero();
        assertThat(MoneyMigration.cents(new Decimal128(new BigDecimal("12.345")))).isEqualTo(1235);
        assertThat(MoneyMigration.cents("7.5")).isEqualTo(750);
    }

    @Test
    void decimalsForJsonAlwaysHaveTwoPlaces() {
        assertThat(Money.toDecimal(4250L)).isEqualByComparingTo("42.50").hasToString("42.50");
        assertThat(Money.toDecimal(5L)).hasToString("0.05");
        assertThat(Money.toDecimal(null)).isNull();
    }

    @Test
    void arithmeticIsExactAndChecksOverflow() {
        assertThat(Money.ofCents(1999).times(3).plus(Money.ofCents(1000)).cents()).isEqualTo(6997);
        assertThat(Money.ofCents(1000).minus(Money.ofCents(1)).toString()).isEqualTo("9.99 RON");
        assertThatThrownBy(() -> Money.times(Long.MAX_VALUE / 2, 3)).isInstanceOf(ArithmeticException.class);
    }
}
