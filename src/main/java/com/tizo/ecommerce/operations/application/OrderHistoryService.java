package com.tizo.ecommerce.operations.application;

import com.tizo.ecommerce.shared.error.DomainException;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderHistoryService {

    private static final Map<String, String> SORTS = Map.of(
            "requestedAt", "cr.requested_at",
            "resolvedAt", "cr.resolved_at",
            "orderDisplayNumber", "cr.order_id");

    private final OrderHistoryQueryPort history;

    public OrderHistoryService(OrderHistoryQueryPort history) {
        this.history = history;
    }

    @Transactional(readOnly = true)
    public OperationsProjection.CancellationPage list(
            String status,
            String search,
            String reasonCode,
            String rejectionCode,
            String requestedByType,
            String operatorId,
            OffsetDateTime createdFrom,
            OffsetDateTime createdTo,
            OffsetDateTime resolvedFrom,
            OffsetDateTime resolvedTo,
            int page,
            int pageSize,
            String sortBy,
            String sortDirection) {
        validateRange(createdFrom, createdTo);
        validateRange(resolvedFrom, resolvedTo);
        String column = SORTS.get(sortBy);
        if (column == null) {
            throw DomainException.validation("INVALID_SORT", "El criterio de ordenamiento no es válido.");
        }
        boolean ascending = switch (sortDirection.toLowerCase()) {
            case "asc" -> true;
            case "desc" -> false;
            default -> throw DomainException.validation(
                    "INVALID_SORT_DIRECTION", "La dirección debe ser asc o desc.");
        };
        return history.find(
                status, normalize(search), reasonCode, rejectionCode, requestedByType, normalize(operatorId),
                createdFrom, createdTo, resolvedFrom, resolvedTo, page, pageSize, column, ascending);
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
