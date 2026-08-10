package com.tizo.ecommerce.sales.application;

import com.tizo.ecommerce.shared.observability.BusinessMetrics;
import com.tizo.ecommerce.shared.persistence.AuditEventJpaAdapter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class OperationalEffectWorker {

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final AuditEventJpaAdapter audit;
    private final BusinessMetrics metrics;
    private final Duration leaseDuration;
    private final int maxAttempts;

    public OperationalEffectWorker(
            JdbcClient jdbc,
            TransactionTemplate transactions,
            AuditEventJpaAdapter audit,
            BusinessMetrics metrics,
            MeterRegistry meterRegistry,
            @Value("${tizo.effects.lease-duration:30s}") Duration leaseDuration,
            @Value("${tizo.effects.max-attempts:5}") int maxAttempts) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.audit = audit;
        this.metrics = metrics;
        this.leaseDuration = leaseDuration;
        this.maxAttempts = maxAttempts;
        registerQueueGauge(meterRegistry, "pending", "PENDING");
        registerQueueGauge(meterRegistry, "processing", "PROCESSING");
        registerQueueGauge(meterRegistry, "failed", "FAILED");
    }

    public int runOnce() {
        List<EffectJob> jobs = transactions.execute(status -> claim(10));
        if (jobs == null) {
            return 0;
        }
        jobs.forEach(job -> transactions.executeWithoutResult(status -> process(job)));
        return jobs.size();
    }

    private List<EffectJob> claim(int limit) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return jdbc.sql("""
                        WITH candidates AS (
                            SELECT id
                            FROM operational_effect
                            WHERE (status='PENDING' AND next_attempt_at<=:now)
                               OR (status='PROCESSING' AND lease_until<:now)
                            ORDER BY next_attempt_at, id
                            FOR UPDATE SKIP LOCKED
                            LIMIT :limit
                        )
                        UPDATE operational_effect effect
                        SET status='PROCESSING', lease_until=:leaseUntil,
                            attempts=attempts+1, updated_at=:now
                        FROM candidates
                        WHERE effect.id=candidates.id
                        RETURNING effect.id, effect.order_id, effect.cancellation_request_id,
                                  effect.effect_type, effect.attempts, effect.payload::text
                        """)
                .param("now", now)
                .param("leaseUntil", now.plus(leaseDuration))
                .param("limit", limit)
                .query((row, number) -> new EffectJob(
                        row.getString("id"), row.getString("order_id"),
                        row.getString("cancellation_request_id"), row.getString("effect_type"),
                        row.getInt("attempts"), row.getString("payload")))
                .list();
    }

    private void process(EffectJob job) {
        long startedAt = System.nanoTime();
        String result = "error";
        try {
            if (job.payload().contains("\"simulateFailure\": true")
                    || job.payload().contains("\"simulateFailure\":true")) {
                throw new EffectProcessingException("SIMULATED_FAILURE");
            }
            if ("REFUND".equals(job.type())) {
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                jdbc.sql("""
                                UPDATE refund
                                SET status='COMPLETED', provider_reference=:reference, updated_at=:now
                                WHERE cancellation_request_id=:requestId AND status<>'COMPLETED'
                                """)
                        .param("reference", "demo-refund-" + job.requestId())
                        .param("now", now)
                        .param("requestId", job.requestId())
                        .update();
            }
            complete(job);
            result = "completed";
        } catch (EffectProcessingException exception) {
            retryOrFail(job, exception.code());
            result = job.attempts() >= maxAttempts ? "failed" : "retry";
        } finally {
            metrics.effectProcessed(
                    job.type(), result, Duration.ofNanos(System.nanoTime() - startedAt));
        }
    }

    private void registerQueueGauge(MeterRegistry registry, String publicStatus, String databaseStatus) {
        Gauge.builder(
                        "tizo.operational.effects.queue.depth",
                        this,
                        worker -> worker.queueDepth(databaseStatus))
                .description("Durable operational effects by queue status")
                .tag("status", publicStatus)
                .register(registry);
    }

    private double queueDepth(String status) {
        try {
            return jdbc.sql("SELECT count(*) FROM operational_effect WHERE status=:status")
                    .param("status", status)
                    .query(Long.class)
                    .single();
        } catch (RuntimeException unavailable) {
            return Double.NaN;
        }
    }

    private void complete(EffectJob job) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.sql("""
                        UPDATE operational_effect
                        SET status='COMPLETED', lease_until=NULL, last_error=NULL, updated_at=:now
                        WHERE id=:id AND status='PROCESSING'
                        """)
                .param("now", now)
                .param("id", job.id())
                .update();
        audit.append(
                "CANCELLATION_REQUEST", job.requestId(), "EFFECT_UPDATED", "SYSTEM", "effect-worker",
                "SUCCESS", java.util.Map.of("effectType", job.type(), "status", "COMPLETED"));
    }

    private void retryOrFail(EffectJob job, String failureCode) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        boolean exhausted = job.attempts() >= maxAttempts;
        jdbc.sql("""
                        UPDATE operational_effect
                        SET status=:status, lease_until=NULL, last_error=:failureCode,
                            next_attempt_at=:nextAttemptAt, updated_at=:now
                        WHERE id=:id AND status='PROCESSING'
                        """)
                .param("status", exhausted ? "FAILED" : "PENDING")
                .param("failureCode", failureCode)
                .param("nextAttemptAt", exhausted ? now : now.plus(backoff(job)))
                .param("now", now)
                .param("id", job.id())
                .update();
    }

    private Duration backoff(EffectJob job) {
        long exponentialSeconds = Math.min(300, 1L << Math.min(8, Math.max(0, job.attempts() - 1)));
        long jitterMillis = Math.floorMod(job.id().hashCode(), 1_000);
        return Duration.ofSeconds(exponentialSeconds).plusMillis(jitterMillis);
    }

    private record EffectJob(
            String id,
            String orderId,
            String requestId,
            String type,
            int attempts,
            String payload) {
    }

    private static final class EffectProcessingException extends RuntimeException {

        private final String code;

        private EffectProcessingException(String code) {
            super(code);
            this.code = code;
        }

        private String code() {
            return code;
        }
    }
}
