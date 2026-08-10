package com.tizo.ecommerce.sales.adapter.in.web;

import com.tizo.ecommerce.generated.model.CurrencyCode;
import com.tizo.ecommerce.generated.model.CustomerCancellationReceipt;
import com.tizo.ecommerce.generated.model.CustomerCancellationReconciliationResponse;
import com.tizo.ecommerce.generated.model.Money;
import com.tizo.ecommerce.sales.application.CustomerCancellationService;
import com.tizo.ecommerce.sales.domain.cancellation.CancellationRequest;
import org.springframework.stereotype.Component;

@Component
public class CustomerCancellationWebMapper {

    public CustomerCancellationReceipt toReceipt(CustomerCancellationService.CancellationResult result) {
        CancellationRequest request = result.request();
        return new CustomerCancellationReceipt(
                request.id(),
                request.orderId(),
                CustomerCancellationReceipt.StatusEnum.PENDING,
                request.items().stream().map(CancellationRequest.Item::orderItemId).toList(),
                new Money(request.affectedAmount().amount(), CurrencyCode.ARS),
                request.requestedAt(),
                result.idempotencyKey(),
                result.created());
    }

    public CustomerCancellationReconciliationResponse toReconciliation(
            CustomerCancellationService.CancellationResult result) {
        return new CustomerCancellationReconciliationResponse(true, toReceipt(result));
    }
}
