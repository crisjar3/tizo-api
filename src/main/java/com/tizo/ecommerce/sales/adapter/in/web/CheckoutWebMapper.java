package com.tizo.ecommerce.sales.adapter.in.web;

import com.tizo.ecommerce.generated.model.CheckoutReconciliationResponse;
import com.tizo.ecommerce.generated.model.CreateOrderResponse;
import com.tizo.ecommerce.sales.application.CheckoutService;
import org.springframework.stereotype.Component;

@Component
public class CheckoutWebMapper {

    private final CustomerOrderWebMapper orders;

    public CheckoutWebMapper(CustomerOrderWebMapper orders) {
        this.orders = orders;
    }

    public CreateOrderResponse toCreateResponse(CheckoutService.CheckoutResult result) {
        return new CreateOrderResponse(
                orders.toDetail(result.order()), result.idempotencyKey(), result.created());
    }

    public CheckoutReconciliationResponse toReconciliation(CheckoutService.CheckoutResult result) {
        return new CheckoutReconciliationResponse(
                true, result.idempotencyKey(), orders.toDetail(result.order()));
    }
}
