package com.tizo.ecommerce.sales.application;

import com.tizo.ecommerce.shared.error.DomainException;
import com.tizo.ecommerce.shared.idempotency.IdempotencyService;
import com.tizo.ecommerce.shared.observability.BusinessMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutReconciliationService {

    private final IdempotencyService idempotency;
    private final BusinessMetrics metrics;

    public CheckoutReconciliationService(IdempotencyService idempotency, BusinessMetrics metrics) {
        this.idempotency = idempotency;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public CheckoutService.CheckoutResult reconcile(String customerId, String key) {
        CheckoutService.CheckoutResult result = idempotency.reconcile(
                        CheckoutService.scope(customerId), key, CheckoutService.CheckoutResult.class)
                .orElseThrow(() -> DomainException.notFound("CHECKOUT_RESULT_NOT_FOUND",
                        "No existe un resultado confirmado para la clave indicada."));
        metrics.uncertainOutcome("reconciled");
        return result;
    }
}
