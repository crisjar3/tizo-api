package com.tizo.ecommerce.sales.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record CreateCancellationCommand(
        String customerId,
        String orderId,
        Set<String> itemIds,
        String reasonCode,
        String reasonNote,
        String idempotencyKey,
        long expectedOrderVersion,
        String requestedByType,
        String requestedById) {

    public CreateCancellationCommand {
        itemIds = Set.copyOf(itemIds);
    }

    public Map<String, Object> logicalPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerId", customerId);
        payload.put("orderId", orderId);
        payload.put("itemIds", itemIds.stream().sorted().toList());
        payload.put("reasonCode", reasonCode);
        payload.put("reasonNote", reasonNote == null ? "" : reasonNote.strip());
        payload.put("expectedOrderVersion", expectedOrderVersion);
        payload.put("requestedByType", requestedByType);
        payload.put("requestedById", requestedById);
        return Map.copyOf(payload);
    }
}
