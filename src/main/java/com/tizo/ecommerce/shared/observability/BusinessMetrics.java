package com.tizo.ecommerce.shared.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BusinessMetrics {

    private final MeterRegistry registry;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void idempotencyReplay(String scope) {
        counter("tizo.idempotency.replays", "scope", scope).increment();
    }

    public void idempotencyConflict(String scope) {
        counter("tizo.idempotency.conflicts", "scope", scope).increment();
    }

    public void cancellationDecision(String result) {
        counter("tizo.cancellation.decisions", "result", result).increment();
    }

    public void effectProcessed(String type, String result) {
        counter("tizo.operational.effects", "type", type, "result", result).increment();
    }

    private Counter counter(String name, String... tags) {
        return registry.counter(name, tags);
    }
}
