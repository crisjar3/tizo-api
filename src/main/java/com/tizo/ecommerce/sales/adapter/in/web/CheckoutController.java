package com.tizo.ecommerce.sales.adapter.in.web;

import com.tizo.ecommerce.generated.model.CheckoutReconciliationResponse;
import com.tizo.ecommerce.generated.model.CreateOrderRequest;
import com.tizo.ecommerce.generated.model.CreateOrderResponse;
import com.tizo.ecommerce.sales.application.CheckoutCommand;
import com.tizo.ecommerce.sales.application.CheckoutReconciliationService;
import com.tizo.ecommerce.sales.application.CheckoutService;
import com.tizo.ecommerce.shared.web.RequestIdentityResolver;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class CheckoutController {

    private final CheckoutService checkout;
    private final CheckoutReconciliationService reconciliation;
    private final CheckoutWebMapper mapper;
    private final RequestIdentityResolver identity;

    public CheckoutController(
            CheckoutService checkout,
            CheckoutReconciliationService reconciliation,
            CheckoutWebMapper mapper,
            RequestIdentityResolver identity) {
        this.checkout = checkout;
        this.reconciliation = reconciliation;
        this.mapper = mapper;
        this.identity = identity;
    }

    ResponseEntity<CreateOrderResponse> create(CreateOrderRequest request) {
        CheckoutService.CheckoutResult result = checkout.checkout(
                new CheckoutCommand(identity.customerId(), request.getIdempotencyKey()));
        return ResponseEntity.created(URI.create("/api/me/orders/" + result.order().id()))
                .body(mapper.toCreateResponse(result));
    }

    ResponseEntity<CheckoutReconciliationResponse> reconcile(String key) {
        return ResponseEntity.ok(mapper.toReconciliation(reconciliation.reconcile(identity.customerId(), key)));
    }
}
