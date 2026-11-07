package com.wovely.wovely.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.wovely.common.error.ApiException;

/** PLAN.md 3.7 — input rules that don't need Mongo. */
class WalletServiceTest {

    final WalletService service = new WalletService(null, null);

    @ParameterizedTest
    @ValueSource(strings = { "0", "-1", "5000.01", "1.001", "10000" })
    void rejectsInvalidTopUps(String amount) {
        assertThatThrownBy(() -> service.topUp("u", new BigDecimal(amount))).isInstanceOf(ApiException.class);
    }

    @ParameterizedTest
    @CsvSource({ "27.5, 2750", "0.1, 10", "19.999, 2000", "45.0, 4500" })
    void legacyAmountsRoundHalfUp(double amount, long cents) {
        // the rule the wallet used before 7.5; MoneyMigration applies the same one to stored doubles
        assertThat(com.wovely.common.money.Money.legacyCents(amount)).isEqualTo(cents);
    }
}
