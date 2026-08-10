package com.tizo.ecommerce.sales.adapter.in.web;

import com.tizo.ecommerce.generated.api.CustomerOrdersApi;
import com.tizo.ecommerce.generated.model.CheckoutReconciliationResponse;
import com.tizo.ecommerce.generated.model.CreateOrderRequest;
import com.tizo.ecommerce.generated.model.CreateOrderResponse;
import com.tizo.ecommerce.generated.model.CustomerOrderDetail;
import com.tizo.ecommerce.generated.model.CustomerOrderListResponse;
import com.tizo.ecommerce.generated.model.OrderStatus;
import com.tizo.ecommerce.sales.application.CustomerOrderQueryService;
import com.tizo.ecommerce.shared.web.RequestIdentityResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomerOrdersController implements CustomerOrdersApi {

    private final CheckoutController checkout;
    private final CustomerOrderQueryService orders;
    private final CustomerOrderWebMapper mapper;
    private final RequestIdentityResolver identity;

    public CustomerOrdersController(
            CheckoutController checkout,
            CustomerOrderQueryService orders,
            CustomerOrderWebMapper mapper,
            RequestIdentityResolver identity) {
        this.checkout = checkout;
        this.orders = orders;
        this.mapper = mapper;
        this.identity = identity;
    }

    @Override
    public ResponseEntity<CreateOrderResponse> createOrder(CreateOrderRequest createOrderRequest) {
        return checkout.create(createOrderRequest);
    }

    @Override
    public ResponseEntity<CustomerOrderDetail> getCustomerOrder(String orderId) {
        return ResponseEntity.ok(mapper.toDetail(orders.get(identity.customerId(), orderId)));
    }

    @Override
    public ResponseEntity<CustomerOrderListResponse> listCustomerOrders(
            OrderStatus status,
            Integer page,
            Integer pageSize,
            String sortBy,
            String sortDirection) {
        return ResponseEntity.ok(mapper.toList(orders.list(
                identity.customerId(),
                status == null ? null : status.getValue(),
                page,
                pageSize,
                sortBy,
                sortDirection)));
    }

    @Override
    public ResponseEntity<CheckoutReconciliationResponse> reconcileCheckout(String idempotencyKey) {
        return checkout.reconcile(idempotencyKey);
    }
}
