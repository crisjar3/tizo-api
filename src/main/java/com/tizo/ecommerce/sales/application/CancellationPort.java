package com.tizo.ecommerce.sales.application;

import com.tizo.ecommerce.sales.domain.cancellation.CancellationRequest;

public interface CancellationPort {

    CancellationRequest createPending(CreateCancellationCommand command);
}
