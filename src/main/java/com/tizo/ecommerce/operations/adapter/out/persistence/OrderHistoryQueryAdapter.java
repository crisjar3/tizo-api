package com.tizo.ecommerce.operations.adapter.out.persistence;

import com.tizo.ecommerce.operations.application.OperationsProjection;
import com.tizo.ecommerce.operations.application.OperationsQueryPort;
import com.tizo.ecommerce.operations.application.OrderHistoryQueryPort;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Repository;

@Repository
public class OrderHistoryQueryAdapter implements OrderHistoryQueryPort {

    private final OperationsQueryPort queries;

    public OrderHistoryQueryAdapter(OperationsQueryPort queries) {
        this.queries = queries;
    }

    @Override
    public OperationsProjection.CancellationPage find(
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
            boolean ascending) {
        return queries.findCancellations(
                status, search, reasonCode, requestedByType, operatorId, createdFrom, createdTo,
                page, pageSize, sortColumn, ascending, true, rejectionCode, resolvedFrom, resolvedTo);
    }
}
