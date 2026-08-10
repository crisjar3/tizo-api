package com.tizo.ecommerce.operations.adapter.in.web;

import com.tizo.ecommerce.generated.model.CancellationHistoryResponse;
import com.tizo.ecommerce.operations.application.OperationsProjection;
import org.springframework.stereotype.Component;

@Component
public class OrderHistoryWebMapper {

    private final OperationsWebMapper operationsMapper;

    public OrderHistoryWebMapper(OperationsWebMapper operationsMapper) {
        this.operationsMapper = operationsMapper;
    }

    public CancellationHistoryResponse toResponse(OperationsProjection.CancellationPage history) {
        return operationsMapper.history(history.page());
    }
}
