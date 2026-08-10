package com.tizo.ecommerce.operations.application;

import com.tizo.ecommerce.operations.domain.Operator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface OperationsQueryPort {

    List<Operator> findOperators(Boolean active, String search);

    OperationsProjection.Page<OperationsProjection.OrderView> findOrders(
            String search,
            String status,
            String cancellationStatus,
            String storeId,
            String hubId,
            OffsetDateTime createdFrom,
            OffsetDateTime createdTo,
            int page,
            int pageSize,
            String sortColumn,
            boolean ascending);

    Optional<OperationsProjection.OrderView> findOrder(String orderId);

    OperationsProjection.CancellationPage findCancellations(
            String status,
            String search,
            String reasonCode,
            String requestedByType,
            String operatorId,
            OffsetDateTime createdFrom,
            OffsetDateTime createdTo,
            int page,
            int pageSize,
            String sortColumn,
            boolean ascending,
            boolean terminalOnly,
            String rejectionCode,
            OffsetDateTime resolvedFrom,
            OffsetDateTime resolvedTo);

    Optional<OperationsProjection.CancellationView> findCancellation(String requestId);
}
