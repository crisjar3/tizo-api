package com.tizo.ecommerce.catalog.domain;

import com.tizo.ecommerce.shared.error.DomainException;
import com.tizo.ecommerce.shared.money.Money;
import java.time.OffsetDateTime;
import java.util.List;

public record Product(
        String id,
        String sku,
        String name,
        String description,
        String category,
        Money price,
        int availableStock,
        boolean active,
        OffsetDateTime createdAt,
        long version,
        List<String> imageUrls,
        List<Attribute> attributes) {

    public Product {
        imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
        if (availableStock < 0) {
            throw new IllegalArgumentException("Available stock cannot be negative");
        }
    }

    public void requireAvailableQuantity(int quantity) {
        if (!active) {
            throw DomainException.validation("PRODUCT_UNAVAILABLE", "El producto no está disponible.");
        }
        if (quantity < 1) {
            throw DomainException.validation("INVALID_QUANTITY", "La cantidad debe ser al menos 1.");
        }
        if (quantity > availableStock) {
            throw DomainException.validation("INSUFFICIENT_STOCK", "La cantidad supera el stock disponible.");
        }
    }

    public boolean available() {
        return active && availableStock > 0;
    }

    public String primaryImageUrl() {
        return imageUrls.isEmpty() ? null : imageUrls.getFirst();
    }

    public record Attribute(String name, String value) {
    }
}
