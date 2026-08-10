package com.tizo.ecommerce.shared.idempotency;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JpaIdempotencyAdapter {

    private final JdbcClient jdbc;

    public JpaIdempotencyAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void acquireTransactionLock(String scope, String key) {
        jdbc.sql("""
                        SELECT 1 AS acquired
                        FROM pg_advisory_xact_lock(hashtextextended(:lockName, 0))
                        """)
                .param("lockName", scope + ":" + key)
                .query(Integer.class)
                .single();
    }

    public Optional<StoredOperation> find(String scope, String key) {
        return jdbc.sql("""
                        SELECT payload_hash, response_status, response_body::text, resource_id,
                               correlation_id, created_at
                        FROM idempotent_operation
                        WHERE scope = :scope AND idempotency_key = :key
                        """)
                .param("scope", scope)
                .param("key", key)
                .query((resultSet, rowNumber) -> new StoredOperation(
                        scope,
                        key,
                        resultSet.getString("payload_hash"),
                        resultSet.getInt("response_status"),
                        resultSet.getString("response_body"),
                        resultSet.getString("resource_id"),
                        resultSet.getString("correlation_id"),
                        resultSet.getObject("created_at", OffsetDateTime.class)))
                .optional();
    }

    public void save(StoredOperation operation) {
        jdbc.sql("""
                        INSERT INTO idempotent_operation
                            (scope, idempotency_key, payload_hash, response_status, response_body,
                             resource_id, correlation_id, created_at)
                        VALUES (:scope, :key, :hash, :status, CAST(:body AS jsonb),
                                :resourceId, :correlationId, :createdAt)
                        """)
                .param("scope", operation.scope())
                .param("key", operation.key())
                .param("hash", operation.payloadHash())
                .param("status", operation.responseStatus())
                .param("body", operation.responseBody())
                .param("resourceId", operation.resourceId())
                .param("correlationId", operation.correlationId())
                .param("createdAt", operation.createdAt())
                .update();
    }

    public record StoredOperation(
            String scope,
            String key,
            String payloadHash,
            int responseStatus,
            String responseBody,
            String resourceId,
            String correlationId,
            OffsetDateTime createdAt) {
    }
}
