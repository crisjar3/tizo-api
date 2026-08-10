package com.tizo.ecommerce.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tizo.ecommerce.shared.error.DomainException;
import com.tizo.ecommerce.shared.money.Money;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogDomainTest {

    @Test
    void productAvailabilityRequiresActiveProductAndPositiveStock() {
        Product available = product(3, true);
        Product exhausted = product(0, true);

        assertThat(available.available()).isTrue();
        assertThat(exhausted.available()).isFalse();
        assertThatThrownBy(() -> exhausted.requireAvailableQuantity(1))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).code())
                .isEqualTo("INSUFFICIENT_STOCK");
    }

    @Test
    void productRejectsQuantityBeyondCurrentStock() {
        assertThatThrownBy(() -> product(2, true).requireAvailableQuantity(3))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).code())
                .isEqualTo("INSUFFICIENT_STOCK");
    }

    private Product product(int stock, boolean active) {
        return new Product("product", "sku", "Name", "Description", "category", Money.ars(100), stock,
                active, OffsetDateTime.parse("2026-01-01T00:00:00Z"), 0,
                List.of("https://example.test/image"), List.of());
    }
}
