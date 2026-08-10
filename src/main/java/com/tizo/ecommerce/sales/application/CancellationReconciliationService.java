package com.tizo.ecommerce.sales.application;

import com.tizo.ecommerce.shared.observability.BusinessMetrics;
import org.springframework.stereotype.Service;

@Service
public class CancellationReconciliationService {

    private final CancellationDecisionService decisions;
    private final BusinessMetrics metrics;

    public CancellationReconciliationService(
            CancellationDecisionService decisions,
            BusinessMetrics metrics) {
        this.decisions = decisions;
        this.metrics = metrics;
    }

    public CancellationDecisionService.MutationResult reconcile(String scope, String idempotencyKey) {
        CancellationDecisionService.MutationResult result = decisions.reconcile(scope, idempotencyKey);
        metrics.uncertainOutcome("reconciled");
        return result;
    }
}
