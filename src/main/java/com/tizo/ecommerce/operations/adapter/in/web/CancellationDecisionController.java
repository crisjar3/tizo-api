package com.tizo.ecommerce.operations.adapter.in.web;

import com.tizo.ecommerce.generated.api.OperationsCancellationsApi;
import com.tizo.ecommerce.generated.model.ActorType;
import com.tizo.ecommerce.generated.model.ApproveCancellationRequest;
import com.tizo.ecommerce.generated.model.CancellationHistoryResponse;
import com.tizo.ecommerce.generated.model.CancellationReasonCode;
import com.tizo.ecommerce.generated.model.CancellationRequestDetail;
import com.tizo.ecommerce.generated.model.CancellationRequestListResponse;
import com.tizo.ecommerce.generated.model.CancellationRequestStatus;
import com.tizo.ecommerce.generated.model.CreateOpsCancellationRequest;
import com.tizo.ecommerce.generated.model.CreateOpsCancellationResponse;
import com.tizo.ecommerce.generated.model.IdempotencyScope;
import com.tizo.ecommerce.generated.model.OpsCancellationReconciliationResponse;
import com.tizo.ecommerce.generated.model.RejectCancellationRequest;
import com.tizo.ecommerce.generated.model.RejectionCode;
import com.tizo.ecommerce.generated.model.ResolveCancellationResponse;
import com.tizo.ecommerce.operations.application.OperationsProjection;
import com.tizo.ecommerce.operations.application.OperationsQueryService;
import com.tizo.ecommerce.sales.application.CancellationDecisionService;
import com.tizo.ecommerce.sales.application.CancellationReconciliationService;
import com.tizo.ecommerce.sales.application.CreateCancellationCommand;
import com.tizo.ecommerce.shared.web.RequestIdentityResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.OffsetDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CancellationDecisionController implements OperationsCancellationsApi {

    private final CancellationDecisionService decisions;
    private final OperationsQueryService queries;
    private final OperationsWebMapper mapper;
    private final RequestIdentityResolver identity;
    private final HttpServletRequest httpRequest;
    private final OrderHistoryController history;
    private final CancellationReconciliationService reconciliation;

    public CancellationDecisionController(
            CancellationDecisionService decisions,
            OperationsQueryService queries,
            OperationsWebMapper mapper,
            RequestIdentityResolver identity,
            HttpServletRequest httpRequest,
            OrderHistoryController history,
            CancellationReconciliationService reconciliation) {
        this.decisions = decisions;
        this.queries = queries;
        this.mapper = mapper;
        this.identity = identity;
        this.httpRequest = httpRequest;
        this.history = history;
        this.reconciliation = reconciliation;
    }

    @Override
    public ResponseEntity<CreateOpsCancellationResponse> createOpsCancellation(
            CreateOpsCancellationRequest request) {
        String operatorId = identity.requireActiveOperator(httpRequest);
        CancellationDecisionService.MutationResult result = decisions.create(new CreateCancellationCommand(
                "",
                request.getOrderId(),
                request.getItemIds(),
                request.getReasonCode().getValue(),
                request.getReasonNote(),
                request.getIdempotencyKey(),
                request.getExpectedOrderVersion(),
                "OPERATOR",
                operatorId));
        OperationsProjection.CancellationView requestView = queries.cancellation(result.requestId());
        return ResponseEntity.created(URI.create("/api/ops/cancellation-requests/" + result.requestId()))
                .body(new CreateOpsCancellationResponse(
                        mapper.cancellationDetail(requestView), request.getIdempotencyKey(), result.created()));
    }

    @Override
    public ResponseEntity<ResolveCancellationResponse> approveCancellationRequest(
            String requestId,
            ApproveCancellationRequest request) {
        String operatorId = identity.requireActiveOperator(httpRequest);
        CancellationDecisionService.MutationResult result = decisions.approve(
                new CancellationDecisionService.DecisionCommand(
                        requestId, operatorId, request.getIdempotencyKey(), request.getExpectedRequestVersion(),
                        request.getExpectedOrderVersion(), null, request.getNote()));
        return ResponseEntity.ok(resolve(result));
    }

    @Override
    public ResponseEntity<ResolveCancellationResponse> rejectCancellationRequest(
            String requestId,
            RejectCancellationRequest request) {
        String operatorId = identity.requireActiveOperator(httpRequest);
        CancellationDecisionService.MutationResult result = decisions.reject(
                new CancellationDecisionService.DecisionCommand(
                        requestId, operatorId, request.getIdempotencyKey(), request.getExpectedRequestVersion(),
                        -1, request.getRejectionCode().getValue(), request.getRejectionNote()));
        return ResponseEntity.ok(resolve(result));
    }

    @Override
    public ResponseEntity<OpsCancellationReconciliationResponse> reconcileOpsCancellation(
            String idempotencyKey,
            IdempotencyScope scope) {
        CancellationDecisionService.MutationResult result = reconciliation.reconcile(
                scope.getValue(), idempotencyKey);
        return ResponseEntity.ok(new OpsCancellationReconciliationResponse(
                true,
                scope,
                mapper.cancellationDetail(queries.cancellation(result.requestId())),
                result.orderId() == null ? null : mapper.orderDetail(queries.order(result.orderId()))));
    }

    @Override
    public ResponseEntity<CancellationRequestDetail> getCancellationRequest(String requestId) {
        return ResponseEntity.ok(mapper.cancellationDetail(queries.cancellation(requestId)));
    }

    @Override
    public ResponseEntity<CancellationRequestListResponse> listCancellationRequests(
            CancellationRequestStatus status,
            String search,
            CancellationReasonCode reasonCode,
            ActorType requestedByType,
            String operatorId,
            OffsetDateTime createdFrom,
            OffsetDateTime createdTo,
            Integer page,
            Integer pageSize,
            String sortBy,
            String sortDirection) {
        return ResponseEntity.ok(mapper.cancellationList(queries.cancellations(
                status == null ? null : status.getValue(),
                search,
                reasonCode == null ? null : reasonCode.getValue(),
                requestedByType == null ? null : requestedByType.getValue(),
                operatorId,
                createdFrom,
                createdTo,
                page,
                pageSize,
                sortBy,
                sortDirection)));
    }

    @Override
    public ResponseEntity<CancellationHistoryResponse> listCancellationHistory(
            String search,
            String status,
            CancellationReasonCode reasonCode,
            RejectionCode rejectionCode,
            ActorType requestedByType,
            String operatorId,
            OffsetDateTime createdFrom,
            OffsetDateTime createdTo,
            OffsetDateTime resolvedFrom,
            OffsetDateTime resolvedTo,
            Integer page,
            Integer pageSize,
            String sortBy,
            String sortDirection) {
        return history.list(
                search, status,
                reasonCode == null ? null : reasonCode.getValue(),
                rejectionCode == null ? null : rejectionCode.getValue(),
                requestedByType == null ? null : requestedByType.getValue(),
                operatorId, createdFrom, createdTo, resolvedFrom, resolvedTo,
                page, pageSize, sortBy, sortDirection);
    }

    private ResolveCancellationResponse resolve(CancellationDecisionService.MutationResult result) {
        return new ResolveCancellationResponse(
                mapper.cancellationDetail(queries.cancellation(result.requestId())),
                mapper.orderDetail(queries.order(result.orderId())),
                result.replayed());
    }
}
