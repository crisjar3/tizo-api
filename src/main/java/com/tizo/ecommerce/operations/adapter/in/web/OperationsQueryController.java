package com.tizo.ecommerce.operations.adapter.in.web;

import com.tizo.ecommerce.generated.api.OperationsOrdersApi;
import com.tizo.ecommerce.generated.api.OperatorsApi;
import com.tizo.ecommerce.generated.model.OperatorListResponse;
import com.tizo.ecommerce.generated.model.OpsOrderDetail;
import com.tizo.ecommerce.generated.model.OpsOrderListResponse;
import com.tizo.ecommerce.generated.model.OrderCancellationStatus;
import com.tizo.ecommerce.generated.model.OrderStatus;
import com.tizo.ecommerce.operations.application.OperationsQueryService;
import java.time.OffsetDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OperationsQueryController implements OperatorsApi, OperationsOrdersApi {

    private final OperationsQueryService queries;
    private final OperationsWebMapper mapper;

    public OperationsQueryController(OperationsQueryService queries, OperationsWebMapper mapper) {
        this.queries = queries;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<OperatorListResponse> listOperators(Boolean active, String search) {
        return ResponseEntity.ok(mapper.operators(queries.operators(active, search)));
    }

    @Override
    public ResponseEntity<OpsOrderDetail> getOpsOrder(String orderId) {
        return ResponseEntity.ok(mapper.orderDetail(queries.order(orderId)));
    }

    @Override
    public ResponseEntity<OpsOrderListResponse> listOpsOrders(
            String search,
            OrderStatus status,
            OrderCancellationStatus cancellationStatus,
            String storeId,
            String hubId,
            OffsetDateTime createdFrom,
            OffsetDateTime createdTo,
            Integer page,
            Integer pageSize,
            String sortBy,
            String sortDirection) {
        return ResponseEntity.ok(mapper.orderList(queries.orders(
                search,
                status == null ? null : status.getValue(),
                cancellationStatus == null ? null : cancellationStatus.getValue(),
                storeId,
                hubId,
                createdFrom,
                createdTo,
                page,
                pageSize,
                sortBy,
                sortDirection)));
    }
}
