package com.tizo.ecommerce.sales.adapter.in.web;

import com.tizo.ecommerce.generated.model.CartItem;
import com.tizo.ecommerce.generated.model.CurrencyCode;
import com.tizo.ecommerce.generated.model.Money;
import com.tizo.ecommerce.sales.domain.cart.Cart;
import org.springframework.stereotype.Component;

@Component
public class CartWebMapper {

    public com.tizo.ecommerce.generated.model.Cart toResponse(Cart cart) {
        return new com.tizo.ecommerce.generated.model.Cart(
                cart.id(),
                cart.customerId(),
                cart.items().stream().map(this::toItem).toList(),
                money(cart.subtotal()),
                cart.totalItems(),
                cart.updatedAt());
    }

    private CartItem toItem(Cart.Item item) {
        return new CartItem(
                item.productId(),
                item.productName(),
                item.imageUrl(),
                money(item.unitPrice()),
                item.quantity(),
                money(item.lineTotal()),
                item.availableStock());
    }

    private Money money(com.tizo.ecommerce.shared.money.Money value) {
        return new Money(value.amount(), CurrencyCode.ARS);
    }
}
