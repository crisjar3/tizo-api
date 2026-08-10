package com.tizo.ecommerce.sales.domain.cart;

import com.tizo.ecommerce.shared.money.Money;
import java.time.OffsetDateTime;
import java.util.List;

public record Cart(String id, String customerId, List<Item> items, OffsetDateTime updatedAt) {

    public Cart {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public Money subtotal() {
        return items.stream().map(Item::lineTotal).reduce(Money.ars(0), Money::add);
    }

    public int totalItems() {
        return items.stream().mapToInt(Item::quantity).sum();
    }

    public record Item(
            String productId,
            String productName,
            String imageUrl,
            Money unitPrice,
            int quantity,
            int availableStock) {

        public Item {
            if (quantity < 1) {
                throw new IllegalArgumentException("Cart item quantity must be positive");
            }
        }

        public Money lineTotal() {
            return unitPrice.multiply(quantity);
        }
    }
}
