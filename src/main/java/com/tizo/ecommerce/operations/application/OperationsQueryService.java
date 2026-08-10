package com.tizo.ecommerce.operations.application;

import com.tizo.ecommerce.operations.domain.Operator;
import com.tizo.ecommerce.shared.error.DomainException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationsQueryService {

    private static final Map<String, String> ORDER_SORTS = Map.of(
            "createdAt", "o.created_at",
            "updatedAt", "o.updated_at",
            "displayNumber", "o.id");
    private static final Map<String, String> CANCELLATION_SORTS = Map.of(
            "requestedAt", "cr.requested_at",
            "resolvedAt", "cr.resolved_at",
            "orderDisplayNumber", "cr.order_id");

    private final OperationsQueryPort queries;

    public OperationsQueryService(OperationsQueryPort queries) {
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    public List<Operator> operators(Boolean active, String search) {
        return queries.findOperators(active, normalize(search));
    }

    @Transactional(readOnly = true)
    public OperationsProjection.Page<OperationsProjection.OrderView> orders(
            String search, String status, String cancellationStatus, String storeId, String hubId,
            OffsetDateTime createdFrom, OffsetDateTime createdTo, int page, int pageSize,
            String sortBy, String sortDirection) {
        validateRange(createdFrom, createdTo);
        return queries.findOrders(
                normalize(search), status, cancellationStatus, normalize(storeId), normalize(hubId),
                createdFrom, createdTo, page, pageSize, sort(ORDER_SORTS, sortBy), ascending(sortDirection));
    }

    @Transactional(readOnly = true)
    public OperationsProjection.OrderView order(String orderId) {
        return queries.findOrder(orderId).orElseThrow(() -> DomainException.notFound(
                "ORDER_NOT_FOUND", "El pedido no existe."));
    }

    @Transactional(readOnly = true)
    public OperationsProjection.CancellationPage cancellations(
            String status, String search, String reasonCode, String requestedByType, String operatorId,
            OffsetDateTime createdFrom, OffsetDateTime createdTo, int page, int pageSize,
            String sortBy, String sortDirection) {
        validateRange(createdFrom, createdTo);
        return queries.findCancellations(
                status, normalize(search), reasonCode, requestedByType, normalize(operatorId),
                createdFrom, createdTo, page, pageSize, sort(CANCELLATION_SORTS, sortBy),
                ascending(sortDirection), false, null, null, null);
    }

    @Transactional(readOnly = true)
    public OperationsProjection.CancellationPage history(
            String status, String search, String reasonCode, String rejectionCode,
            String requestedByType, String operatorId, OffsetDateTime createdFrom, OffsetDateTime createdTo,
            OffsetDateTime resolvedFrom, OffsetDateTime resolvedTo, int page, int pageSize,
            String sortBy, String sortDirection) {
        validateRange(createdFrom, createdTo);
        validateRange(resolvedFrom, resolvedTo);
        return queries.findCancellations(
                status, normalize(search), reasonCode, requestedByType, normalize(operatorId),
                createdFrom, createdTo, page, pageSize, sort(CANCELLATION_SORTS, sortBy),
                ascending(sortDirection), true, rejectionCode, resolvedFrom, resolvedTo);
    }

    @Transactional(readOnly = true)
    public OperationsProjection.CancellationView cancellation(String requestId) {
        return queries.findCancellation(requestId).orElseThrow(() -> DomainException.notFound(
                "CANCELLATION_REQUEST_NOT_FOUND", "La solicitud de cancelación no existe."));
    }

    private String sort(Map<String, String> allowed, String requested) {
        String column = allowed.get(requested);
        if (column == null) {
            throw DomainException.validation("INVALID_SORT", "El criterio de ordenamiento no es válido.");
        }
        return column;
    }

    private boolean ascending(String direction) {
        if ("asc".equalsIgnoreCase(direction)) {
            return true;
        }
        if ("desc".equalsIgnoreCase(direction)) {
            return false;
        }
        throw DomainException.validation("INVALID_SORT_DIRECTION", "La dirección debe ser asc o desc.");
    }

    private void validateRange(OffsetDateTime from, OffsetDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw DomainException.validation("INVALID_DATE_RANGE", "La fecha inicial no puede superar la final.");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
