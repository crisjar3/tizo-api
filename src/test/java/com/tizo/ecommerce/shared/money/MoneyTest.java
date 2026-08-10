package com.tizo.ecommerce.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void keepsAmountsInIntegerMinorUnits() {
        assertThat(Money.ars(2_599_000).multiply(2)).isEqualTo(Money.ars(5_198_000));
    }

    @Test
    void rejectsNegativeAmountsAndUnsupportedCurrencies() {
        assertThatThrownBy(() -> new Money(-1, Money.ARS)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Money(1, "USD")).isInstanceOf(IllegalArgumentException.class);
    }
}
