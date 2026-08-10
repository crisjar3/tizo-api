package com.tizo.ecommerce.shared.persistence;

import com.tizo.ecommerce.shared.observability.CorrelationIdFilter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class AuditEventJpaAdapter {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public AuditEventJpaAdapter(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void append(
            String aggregateType,
            String aggregateId,
            String action,
            String actorType,
            String actorId,
            String outcome,
            Map<String, Object> details) {
        jdbc.sql("""
                        INSERT INTO audit_event
                            (id, aggregate_type, aggregate_id, action, actor_type, actor_id,
                             outcome, correlation_id, details, occurred_at)
                        VALUES (:id, :aggregateType, :aggregateId, :action, :actorType, :actorId,
                                :outcome, :correlationId, CAST(:details AS jsonb), :occurredAt)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("aggregateType", aggregateType)
                .param("aggregateId", aggregateId)
                .param("action", action)
                .param("actorType", actorType)
                .param("actorId", actorId)
                .param("outcome", outcome)
                .param("correlationId", CorrelationIdFilter.current())
                .param("details", json(details == null ? Map.of() : details))
                .param("occurredAt", OffsetDateTime.now(ZoneOffset.UTC))
                .update();
    }

    private String json(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cannot serialize audit details", exception);
        }
    }
}
