package com.tizo.ecommerce.support;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    private static final boolean USE_LOCAL_DATABASE = Boolean.getBoolean("tizo.test.local");
    private static final PostgreSQLContainer POSTGRES;

    static {
        if (USE_LOCAL_DATABASE) {
            POSTGRES = null;
        } else {
            POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine")
                    .withDatabaseName("tizo_test")
                    .withUsername("tizo")
                    .withPassword("tizo-test-only");
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        if (USE_LOCAL_DATABASE) {
            registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/tizo_test");
            registry.add("spring.datasource.username", () -> "tizo");
            registry.add("spring.datasource.password", () -> "tizo-local-only");
        } else {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        }
    }

    @Autowired(required = false)
    private JdbcClient jdbc;

    @Autowired(required = false)
    private TransactionTemplate transactions;

    @AfterEach
    void cleanMutableTables() {
        if (jdbc == null || transactions == null) {
            return;
        }
        transactions.executeWithoutResult(status -> jdbc.sql("""
                TRUNCATE TABLE operational_effect, refund, audit_event, idempotent_operation,
                    cancellation_request_item, cancellation_request, order_item, customer_order,
                    cart_item RESTART IDENTITY CASCADE
                """).update());
        transactions.executeWithoutResult(status -> jdbc.sql("""
                UPDATE product SET stock = CASE id
                    WHEN 'product-001' THEN 25
                    WHEN 'product-002' THEN 18
                    WHEN 'product-003' THEN 12
                    WHEN 'product-004' THEN 8
                    WHEN 'product-005' THEN 0
                    ELSE stock END,
                    version = 0,
                    updated_at = '2026-01-01T00:00:00Z'
                """).update());
    }
}
