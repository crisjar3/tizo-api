package com.tizo.ecommerce.sales.adapter.in.web;

import com.tizo.ecommerce.generated.api.CustomerCancellationsApi;
import com.tizo.ecommerce.generated.model.CreateCustomerCancellationRequest;
import com.tizo.ecommerce.generated.model.CustomerCancellationReceipt;
import com.tizo.ecommerce.generated.model.CustomerCancellationReconciliationResponse;
import com.tizo.ecommerce.sales.application.CreateCancellationCommand;
import com.tizo.ecommerce.sales.application.CustomerCancellationService;
import com.tizo.ecommerce.shared.web.RequestIdentityResolver;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomerCancellationController implements CustomerCancellationsApi {

    private final CustomerCancellationService cancellations;
    private final CustomerCancellationWebMapper mapper;
    private final RequestIdentityResolver identity;

    public CustomerCancellationController(
            CustomerCancellationService cancellations,
            CustomerCancellationWebMapper mapper,
            RequestIdentityResolver identity) {
        this.cancellations = cancellations;
        this.mapper = mapper;
        this.identity = identity;
    }

    @Override
    public ResponseEntity<CustomerCancellationReceipt> createCustomerCancellation(
            String orderId,
            CreateCustomerCancellationRequest request) {
        CustomerCancellationService.CancellationResult result = cancellations.create(
                new CreateCancellationCommand(
                        identity.customerId(),
                        orderId,
                        request.getItemIds(),
                        request.getReasonCode().getValue(),
                        request.getReasonNote(),
                        request.getIdempotencyKey(),
                        request.getExpectedOrderVersion()));
        return ResponseEntity.created(URI.create("/api/me/orders/" + result.request().orderId()))
                .body(mapper.toReceipt(result));
    }

    @Override
    public ResponseEntity<CustomerCancellationReconciliationResponse> reconcileCustomerCancellation(
            String idempotencyKey) {
        return ResponseEntity.ok(mapper.toReconciliation(
                cancellations.reconcile(identity.customerId(), idempotencyKey)));
    }
}
