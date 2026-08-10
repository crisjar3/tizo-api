package com.tizo.ecommerce.shared.idempotency;

import com.tizo.ecommerce.shared.error.DomainException;
import com.tizo.ecommerce.shared.observability.BusinessMetrics;
import com.tizo.ecommerce.shared.observability.CorrelationIdFilter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

@Service
public class IdempotencyService {

    private final JpaIdempotencyAdapter adapter;
    private final ObjectMapper canonicalMapper;
    private final BusinessMetrics metrics;

    public IdempotencyService(
            JpaIdempotencyAdapter adapter,
            ObjectMapper objectMapper,
            BusinessMetrics metrics) {
        this.adapter = adapter;
        this.metrics = metrics;
        this.canonicalMapper = objectMapper.rebuild()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();
    }

    public <T> T execute(
            String scope,
            String key,
            Object logicalPayload,
            int responseStatus,
            Class<T> responseType,
            Supplier<T> action,
            Function<T, String> resourceId) {
        validateKey(key);
        String hash = hash(logicalPayload);
        adapter.acquireTransactionLock(scope, key);
        Optional<JpaIdempotencyAdapter.StoredOperation> existing = adapter.find(scope, key);
        if (existing.isPresent()) {
            return replay(scope, existing.get(), hash, responseType);
        }

        T response = action.get();
        adapter.save(new JpaIdempotencyAdapter.StoredOperation(
                scope,
                key,
                hash,
                responseStatus,
                write(response),
                resourceId.apply(response),
                CorrelationIdFilter.current(),
                OffsetDateTime.now(ZoneOffset.UTC)));
        return response;
    }

    public <T> Optional<T> reconcile(String scope, String key, Class<T> responseType) {
        validateKey(key);
        return adapter.find(scope, key).map(stored -> read(stored.responseBody(), responseType));
    }

    public String hash(Object logicalPayload) {
        try {
            byte[] canonical = canonicalMapper.writeValueAsBytes(logicalPayload);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JacksonException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot canonicalize idempotent payload", exception);
        }
    }

    private <T> T replay(
            String scope,
            JpaIdempotencyAdapter.StoredOperation stored,
            String currentHash,
            Class<T> responseType) {
        if (!stored.payloadHash().equals(currentHash)) {
            metrics.idempotencyConflict(scope);
            throw DomainException.conflict("IDEMPOTENCY_KEY_REUSED",
                    "La clave idempotente ya fue usada con otra intención.");
        }
        metrics.idempotencyReplay(scope);
        return read(stored.responseBody(), responseType);
    }

    private String write(Object value) {
        try {
            return canonicalMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cannot store idempotent response", exception);
        }
    }

    private <T> T read(String value, Class<T> responseType) {
        try {
            return canonicalMapper.readValue(value.getBytes(StandardCharsets.UTF_8), responseType);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cannot restore idempotent response", exception);
        }
    }

    private void validateKey(String key) {
        if (key == null || key.length() < 8 || key.length() > 128) {
            throw DomainException.validation("INVALID_IDEMPOTENCY_KEY",
                    "La clave idempotente debe tener entre 8 y 128 caracteres.");
        }
    }
}
