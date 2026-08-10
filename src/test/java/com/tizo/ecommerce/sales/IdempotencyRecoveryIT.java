package com.tizo.ecommerce.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tizo.ecommerce.shared.idempotency.IdempotencyService;
import com.tizo.ecommerce.support.PostgresIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class IdempotencyRecoveryIT extends PostgresIntegrationTest {

    @Autowired
    private IdempotencyService idempotency;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void failureBeforeCommitLeavesNeitherMutationNorSnapshot() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> idempotency.execute(
                        "RECOVERY_TEST",
                        "recovery-before-001",
                        Map.of("intent", "before"),
                        200,
                        RecoveryResult.class,
                        () -> {
                            jdbc.sql("UPDATE product SET stock=stock-1 WHERE id='product-001'").update();
                            throw new SimulatedTimeout();
                        },
                        RecoveryResult::resourceId)))
                .isInstanceOf(SimulatedTimeout.class);

        assertThat(jdbc.sql("SELECT stock FROM product WHERE id='product-001'")
                .query(Integer.class).single()).isEqualTo(25);
        assertThat(idempotency.reconcile(
                "RECOVERY_TEST", "recovery-before-001", RecoveryResult.class)).isEmpty();
    }

    @Test
    void resultCanBeReconciledAfterCommitWhenTheResponseWasLost() throws Exception {
        RecoveryResult committed = transactions.execute(status -> idempotency.execute(
                "RECOVERY_TEST",
                "recovery-after-0001",
                Map.of("intent", "after"),
                200,
                RecoveryResult.class,
                () -> new RecoveryResult("resource-001", "CONFIRMED"),
                RecoveryResult::resourceId));

        RecoveryResult reconciled = idempotency.reconcile(
                        "RECOVERY_TEST", "recovery-after-0001", RecoveryResult.class)
                .orElseThrow();
        assertThat(objectMapper.writeValueAsBytes(reconciled))
                .isEqualTo(objectMapper.writeValueAsBytes(committed));
    }

    record RecoveryResult(String resourceId, String status) {
    }

    private static final class SimulatedTimeout extends RuntimeException {
    }
}
