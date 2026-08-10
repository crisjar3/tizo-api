package com.tizo.ecommerce.sales.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tizo.ecommerce.sales.domain.cart.Cart;
import com.tizo.ecommerce.shared.money.Money;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CartDomainTest {

    @Test
    void calculatesSubtotalAndTotalUnitsFromLines() {
        Cart cart = new Cart("cart", "customer", List.of(
                new Cart.Item("p1", "One", null, Money.ars(100), 2, 5),
                new Cart.Item("p2", "Two", null, Money.ars(250), 1, 2)),
                OffsetDateTime.parse("2026-01-01T00:00:00Z"));

        assertThat(cart.subtotal()).isEqualTo(Money.ars(450));
        assertThat(cart.totalItems()).isEqualTo(3);
    }

    @Test
    void rejectsNonPositiveLineQuantityEvenOutsideHttp() {
        assertThatThrownBy(() -> new Cart.Item("p1", "One", null, Money.ars(100), 0, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
