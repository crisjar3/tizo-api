package com.tizo.ecommerce.sales.application;

import com.tizo.ecommerce.sales.domain.cancellation.CancellationRequest;
import com.tizo.ecommerce.shared.error.DomainException;
import com.tizo.ecommerce.shared.idempotency.IdempotencyService;
import com.tizo.ecommerce.shared.persistence.AuditEventJpaAdapter;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerCancellationService {

    private final CancellationPort cancellations;
    private final IdempotencyService idempotency;
    private final AuditEventJpaAdapter audit;

    public CustomerCancellationService(
            CancellationPort cancellations,
            IdempotencyService idempotency,
            AuditEventJpaAdapter audit) {
        this.cancellations = cancellations;
        this.idempotency = idempotency;
        this.audit = audit;
    }

    @Transactional
    public CancellationResult create(CreateCancellationCommand command) {
        return idempotency.execute(
                scope(command.customerId()),
                command.idempotencyKey(),
                command.logicalPayload(),
                201,
                CancellationResult.class,
                () -> createOnce(command),
                result -> result.request().id());
    }

    @Transactional(readOnly = true)
    public CancellationResult reconcile(String customerId, String idempotencyKey) {
        return idempotency.reconcile(scope(customerId), idempotencyKey, CancellationResult.class)
                .orElseThrow(() -> DomainException.notFound(
                        "CANCELLATION_RESULT_NOT_FOUND",
                        "No existe un resultado confirmado para la clave indicada."));
    }

    static String scope(String customerId) {
        return "CUSTOMER_CANCELLATION:" + customerId;
    }

    private CancellationResult createOnce(CreateCancellationCommand command) {
        CancellationRequest request = cancellations.createPending(command);
        audit.append(
                "CANCELLATION_REQUEST",
                request.id(),
                "CANCELLATION_REQUESTED",
                "CUSTOMER",
                command.customerId(),
                "SUCCESS",
                Map.of("orderId", command.orderId(), "itemIds", command.itemIds()));
        return new CancellationResult(request, command.idempotencyKey(), true);
    }

    public record CancellationResult(
            CancellationRequest request,
            String idempotencyKey,
            boolean created) {
    }
}
