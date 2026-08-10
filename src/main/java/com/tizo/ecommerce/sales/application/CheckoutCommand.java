package com.tizo.ecommerce.sales.application;

import java.util.Map;

public record CheckoutCommand(String customerId, String idempotencyKey) {

    public Map<String, Object> logicalPayload() {
        return Map.of("customerId", customerId);
    }
}
