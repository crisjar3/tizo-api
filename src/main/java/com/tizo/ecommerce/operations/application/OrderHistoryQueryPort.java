package com.tizo.ecommerce.operations.application;

import java.time.OffsetDateTime;

public interface OrderHistoryQueryPort {

    OperationsProjection.CancellationPage find(
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
            String sortColumn,
            boolean ascending);
}
