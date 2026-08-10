package com.tizo.ecommerce.sales.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tizo.ecommerce.sales.domain.cancellation.CancellationRequest;
import com.tizo.ecommerce.sales.domain.order.Order;
import com.tizo.ecommerce.shared.error.DomainException;
import com.tizo.ecommerce.shared.money.Money;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CancellationDomainTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(
            2026, 8, 9, 19, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void createsPendingRequestForCompleteLinesAndDerivesAmount() {
        Order order = order(null, 7, List.of(
                item("item-001", 2, 200_000, "CONFIRMED"),
                item("item-002", 1, 500_000, "PREPARING")));

        CancellationRequest request = CancellationRequest.pending(
                "request-001",
                order,
                Set.of("item-001"),
                "CUSTOMER_REQUEST",
                "Ya no lo necesito",
                "CUSTOMER",
                "customer-001",
                7,
                NOW);

        assertThat(request.status()).isEqualTo("PENDING");
        assertThat(request.items()).containsExactly(
                new CancellationRequest.Item("item-001", 2, Money.ars(400_000)));
        assertThat(request.affectedAmount()).isEqualTo(Money.ars(400_000));
    }

    @Test
    void rejectsStaleVersionUnknownLinesAndNonCancellableStates() {
        Order order = order(null, 3, List.of(item("item-001", 1, 200_000, "ON_THE_WAY")));

        assertDomainCode(() -> CancellationRequest.pending(
                "request-001", order, Set.of("item-001"), "CUSTOMER_REQUEST", null,
                "CUSTOMER", "customer-001", 2, NOW), "STALE_ORDER_VERSION");
        assertDomainCode(() -> CancellationRequest.pending(
                "request-001", order, Set.of("missing"), "CUSTOMER_REQUEST", null,
                "CUSTOMER", "customer-001", 3, NOW), "CANCELLATION_ITEM_NOT_FOUND");
        assertDomainCode(() -> CancellationRequest.pending(
                "request-001", order, Set.of("item-001"), "CUSTOMER_REQUEST", null,
                "CUSTOMER", "customer-001", 3, NOW), "ITEM_NOT_CANCELLABLE");
    }

    @Test
    void rejectsOrdersThatAlreadyLeftForDelivery() {
        Order order = order(NOW, 1, List.of(item("item-001", 1, 200_000, "CONFIRMED")));

        assertDomainCode(() -> CancellationRequest.pending(
                "request-001", order, Set.of("item-001"), "CUSTOMER_REQUEST", null,
                "CUSTOMER", "customer-001", 1, NOW), "ORDER_ALREADY_DISPATCHED");
    }

    private void assertDomainCode(Runnable action, String expectedCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).code())
                .isEqualTo(expectedCode);
    }

    private Order order(OffsetDateTime dispatchedAt, long version, List<Order.Item> items) {
        return new Order(
                "order-001", "customer-001", dispatchedAt == null ? "AWAITING_STORES" : "DISPATCHED",
                "NONE", Money.ars(900_000), Money.ars(900_000), "DEMO",
                new Order.Address("Cliente", "Calle 1", null, "Ciudad", "Región", "1000", "AR", null),
                dispatchedAt, NOW, NOW, version, items, null);
    }

    private Order.Item item(String id, int quantity, long unitPrice, String status) {
        Money total = Money.ars(unitPrice).multiply(quantity);
        return new Order.Item(
                id, "product-001", "Producto", "SKU-001", "https://example.test/item.jpg",
                quantity, Money.ars(unitPrice), total, status);
    }
}
