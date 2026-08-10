package com.tizo.ecommerce.sales.application;

import com.tizo.ecommerce.shared.error.DomainException;
import com.tizo.ecommerce.shared.idempotency.IdempotencyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutReconciliationService {

    private final IdempotencyService idempotency;

    public CheckoutReconciliationService(IdempotencyService idempotency) {
        this.idempotency = idempotency;
    }

    @Transactional(readOnly = true)
    public CheckoutService.CheckoutResult reconcile(String customerId, String key) {
        return idempotency.reconcile(CheckoutService.scope(customerId), key, CheckoutService.CheckoutResult.class)
                .orElseThrow(() -> DomainException.notFound("CHECKOUT_RESULT_NOT_FOUND",
                        "No existe un resultado confirmado para la clave indicada."));
    }
}
