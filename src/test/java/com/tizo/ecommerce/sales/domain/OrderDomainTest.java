package com.tizo.ecommerce.sales.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.tizo.ecommerce.sales.domain.order.Order;
import com.tizo.ecommerce.shared.money.Money;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderDomainTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(
            2026, 8, 9, 18, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void exposesInitialCheckoutStateAndDerivedTotals() {
        Order order = order(List.of(
                item("item-1", 2, 100_000, "CONFIRMED"),
                item("item-2", 1, 250_000, "PREPARING")));

        assertThat(order.status()).isEqualTo("AWAITING_STORES");
        assertThat(order.cancellationStatus()).isEqualTo("NONE");
        assertThat(order.progressStatus()).isEqualTo("PENDING");
        assertThat(order.totalItems()).isEqualTo(3);
        assertThat(order.cancelledItems()).isZero();
        assertThat(order.displayNumber()).isEqualTo("TZ-1234567890");
        assertThat(order.items()).isUnmodifiable();
    }

    @Test
    void derivesProgressFromTheLeastAdvancedActiveItemAndIgnoresCancelledItems() {
        Order order = order(List.of(
                item("item-1", 1, 100_000, "CANCELLED"),
                item("item-2", 1, 250_000, "ON_THE_WAY"),
                item("item-3", 1, 300_000, "DELIVERED")));

        assertThat(order.progressStatus()).isEqualTo("IN_TRANSIT_TO_HUB");
        assertThat(order.cancelledItems()).isEqualTo(1);
    }

    private Order order(List<Order.Item> items) {
        return new Order(
                "12345678-90ab-cdef-1234-567890abcdef",
                "customer-001",
                "AWAITING_STORES",
                "NONE",
                Money.ars(750_000),
                Money.ars(750_000),
                "DEMO",
                new Order.Address(
                        "Cliente Demo", "Calle 1", null, "Buenos Aires", "CABA",
                        "1000", "AR", "+541100000000"),
                null,
                NOW,
                NOW,
                0,
                items,
                null);
    }

    private Order.Item item(String id, int quantity, long unitPrice, String status) {
        return new Order.Item(
                id,
                "product-" + id,
                "Producto " + id,
                "SKU-" + id,
                "https://example.test/" + id + ".jpg",
                quantity,
                Money.ars(unitPrice),
                "CANCELLED".equals(status) ? Money.ars(0) : Money.ars(unitPrice * quantity),
                status);
    }
}
