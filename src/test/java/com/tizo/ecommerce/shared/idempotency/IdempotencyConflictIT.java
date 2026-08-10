package com.tizo.ecommerce.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tizo.ecommerce.shared.error.DomainException;
import com.tizo.ecommerce.support.PostgresIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class IdempotencyConflictIT extends PostgresIntegrationTest {

    @Autowired
    private IdempotencyService idempotency;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void sameScopeAndKeyWithDifferentLogicalPayloadIsAStableConflict() {
        transactions.executeWithoutResult(status -> idempotency.execute(
                "CONFLICT_TEST",
                "conflicting-key-001",
                Map.of("decision", "APPROVE"),
                200,
                Result.class,
                () -> new Result("request-001"),
                Result::resourceId));

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> idempotency.execute(
                        "CONFLICT_TEST",
                        "conflicting-key-001",
                        Map.of("decision", "REJECT"),
                        200,
                        Result.class,
                        () -> new Result("request-002"),
                        Result::resourceId)))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.code())
                                .isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    }

    record Result(String resourceId) {
    }
}
