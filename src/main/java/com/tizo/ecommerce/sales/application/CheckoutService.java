package com.tizo.ecommerce.sales.application;

import com.tizo.ecommerce.sales.domain.order.Order;
import com.tizo.ecommerce.shared.idempotency.IdempotencyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutService {

    private final CheckoutPort checkout;
    private final IdempotencyService idempotency;

    public CheckoutService(CheckoutPort checkout, IdempotencyService idempotency) {
        this.checkout = checkout;
        this.idempotency = idempotency;
    }

    @Transactional
    public CheckoutResult checkout(CheckoutCommand command) {
        return idempotency.execute(
                scope(command.customerId()),
                command.idempotencyKey(),
                command.logicalPayload(),
                201,
                CheckoutResult.class,
                () -> new CheckoutResult(checkout.createOrderFromCart(command.customerId()),
                        command.idempotencyKey(), true),
                result -> result.order().id());
    }

    static String scope(String customerId) {
        return "CHECKOUT:" + customerId;
    }

    public record CheckoutResult(Order order, String idempotencyKey, boolean created) {
    }
}
