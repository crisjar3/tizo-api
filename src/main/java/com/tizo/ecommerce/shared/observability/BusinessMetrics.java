package com.tizo.ecommerce.shared.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
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

    public void concurrencyConflict(String kind) {
        counter("tizo.business.concurrency.conflicts", "kind", kind).increment();
    }

    public void uncertainOutcome(String result) {
        counter("tizo.idempotency.uncertain.outcomes", "result", result).increment();
    }

    public void rateLimitRequest(String result) {
        counter("tizo.rate.limit.requests", "result", result).increment();
    }

    public void effectProcessed(String type, String result, Duration duration) {
        counter("tizo.operational.effects", "type", type, "result", result).increment();
        registry.timer("tizo.operational.effects.duration", "type", type, "result", result)
                .record(duration);
    }

    private Counter counter(String name, String... tags) {
        return registry.counter(name, tags);
    }
}
