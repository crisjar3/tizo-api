package com.tizo.ecommerce.operations.adapter.in.web;

import com.tizo.ecommerce.generated.model.CancellationHistoryResponse;
import com.tizo.ecommerce.operations.application.OrderHistoryService;
import java.time.OffsetDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class OrderHistoryController {

    private final OrderHistoryService history;
    private final OrderHistoryWebMapper mapper;

    public OrderHistoryController(OrderHistoryService history, OrderHistoryWebMapper mapper) {
        this.history = history;
        this.mapper = mapper;
    }

    public ResponseEntity<CancellationHistoryResponse> list(
            String search,
            String status,
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
        return ResponseEntity.ok(mapper.toResponse(history.list(
                status, search, reasonCode, rejectionCode, requestedByType, operatorId,
                createdFrom, createdTo, resolvedFrom, resolvedTo, page, pageSize, sortBy, sortDirection)));
    }
}
