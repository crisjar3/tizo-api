package com.tizo.ecommerce.sales.application;

import com.tizo.ecommerce.sales.domain.cancellation.CancellationRequest;
import com.tizo.ecommerce.shared.error.DomainException;
import com.tizo.ecommerce.shared.idempotency.IdempotencyService;
import com.tizo.ecommerce.shared.observability.BusinessMetrics;
import com.tizo.ecommerce.shared.persistence.AuditEventJpaAdapter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancellationDecisionService {

    private final CancellationPort cancellations;
    private final CancellationDecisionPort decisions;
    private final IdempotencyService idempotency;
    private final AuditEventJpaAdapter audit;
    private final BusinessMetrics metrics;

    public CancellationDecisionService(
            CancellationPort cancellations,
            CancellationDecisionPort decisions,
            IdempotencyService idempotency,
            AuditEventJpaAdapter audit,
            BusinessMetrics metrics) {
        this.cancellations = cancellations;
        this.decisions = decisions;
        this.idempotency = idempotency;
        this.audit = audit;
        this.metrics = metrics;
    }

    @Transactional
    public MutationResult create(CreateCancellationCommand command) {
        return idempotency.execute(
                scope("CREATE"), command.idempotencyKey(), command.logicalPayload(), 201,
                MutationResult.class,
                () -> {
                    CancellationRequest request = cancellations.createPending(command);
                    audit.append(
                            "CANCELLATION_REQUEST", request.id(), "REQUEST_CREATED", "OPERATOR",
                            command.requestedById(), "SUCCESS", Map.of("orderId", request.orderId()));
                    return new MutationResult("CREATE", request.id(), request.orderId(), true, false);
                },
                MutationResult::requestId);
    }

    @Transactional
    public MutationResult approve(DecisionCommand command) {
        return idempotency.execute(
                scope("APPROVE"), command.idempotencyKey(), command.logicalPayload(), 200,
                MutationResult.class,
                () -> {
                    CancellationDecisionPort.DecisionReference reference = decisions.approve(
                            command.requestId(), command.operatorId(), command.expectedRequestVersion(),
                            command.expectedOrderVersion(), command.note());
                    audit.append(
                            "CANCELLATION_REQUEST", reference.requestId(), "REQUEST_APPROVED", "OPERATOR",
                            command.operatorId(), "SUCCESS", Map.of("orderId", reference.orderId()));
                    audit.append(
                            "CANCELLATION_REQUEST", reference.requestId(), "CANCELLATION_COMPLETED", "SYSTEM",
                            "tizo-api", "SUCCESS", Map.of("orderId", reference.orderId()));
                    metrics.cancellationDecision("approved");
                    return new MutationResult(
                            "APPROVE", reference.requestId(), reference.orderId(), false, false);
                },
                MutationResult::requestId);
    }

    @Transactional
    public MutationResult reject(DecisionCommand command) {
        return idempotency.execute(
                scope("REJECT"), command.idempotencyKey(), command.logicalPayload(), 200,
                MutationResult.class,
                () -> {
                    CancellationDecisionPort.DecisionReference reference = decisions.reject(
                            command.requestId(), command.operatorId(), command.expectedRequestVersion(),
                            command.rejectionCode(), command.note());
                    audit.append(
                            "CANCELLATION_REQUEST", reference.requestId(), "REQUEST_REJECTED", "OPERATOR",
                            command.operatorId(), "SUCCESS", Map.of("orderId", reference.orderId()));
                    metrics.cancellationDecision("rejected");
                    return new MutationResult(
                            "REJECT", reference.requestId(), reference.orderId(), false, false);
                },
                MutationResult::requestId);
    }

    @Transactional(readOnly = true)
    public MutationResult reconcile(String requestedScope, String key) {
        return idempotency.reconcile(scope(requestedScope), key, MutationResult.class)
                .orElseThrow(() -> DomainException.notFound(
                        "CANCELLATION_RESULT_NOT_FOUND",
                        "No existe un resultado confirmado para la clave y alcance indicados."));
    }

    static String scope(String operation) {
        return "OPS_" + operation;
    }

    public record DecisionCommand(
            String requestId,
            String operatorId,
            String idempotencyKey,
            long expectedRequestVersion,
            long expectedOrderVersion,
            String rejectionCode,
            String note) {

        public Map<String, Object> logicalPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("requestId", requestId);
            payload.put("operatorId", operatorId);
            payload.put("expectedRequestVersion", expectedRequestVersion);
            payload.put("expectedOrderVersion", expectedOrderVersion);
            payload.put("rejectionCode", rejectionCode == null ? "" : rejectionCode);
            payload.put("note", note == null ? "" : note.strip());
            return Map.copyOf(payload);
        }
    }

    public record MutationResult(
            String operation,
            String requestId,
            String orderId,
            boolean created,
            boolean replayed) {
    }
}
