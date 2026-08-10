package com.tizo.ecommerce.demo.application;

import com.tizo.ecommerce.demo.domain.DemoScenario;
import com.tizo.ecommerce.demo.domain.DemoScenarioState;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!production")
@ConditionalOnProperty(
        name = {"tizo.demo.enabled", "tizo.demo.tools-enabled"},
        havingValue = "true")
public class DemoResetService {

    private static final long RESET_LOCK_ID = 8_149_522_026L;
    private static final int SCHEMA_VERSION = 2;

    private final JdbcTemplate jdbc;
    private final DemoScenarioState scenarioState;
    private final Clock clock;

    public DemoResetService(DataSource dataSource, DemoScenarioState scenarioState) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.scenarioState = scenarioState;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public ResetResult reset(DemoScenario scenario) {
        jdbc.query("SELECT pg_advisory_xact_lock(?)", (ResultSetExtractor<Void>) resultSet -> null, RESET_LOCK_ID);
        jdbc.execute("""
                TRUNCATE TABLE operational_effect, refund, audit_event, idempotent_operation,
                    cancellation_request_item, cancellation_request, order_item, customer_order,
                    cart_item, cart, product_attribute, product_image, operator_account,
                    fulfillment_hub, store, customer_address, customer, product
                RESTART IDENTITY CASCADE
                """);
        executeSeed();
        configureScenario(scenario);
        scenarioState.activate(scenario);
        return new ResetResult(clock.instant(), SCHEMA_VERSION, scenario);
    }

    private void executeSeed() {
        jdbc.execute((Connection connection) -> {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/local/R__demo_seed.sql"));
            return null;
        });
    }

    private void configureScenario(DemoScenario scenario) {
        switch (scenario) {
            case EMPTY -> jdbc.update("UPDATE product SET active = FALSE, version = version + 1");
            case CONCURRENT_RESOLUTION -> seedOrder(true, false);
            case DISPATCHED_ORDER -> seedOrder(false, true);
            case NORMAL, SLOW, SERVER_ERROR, TIMEOUT_BEFORE_COMMIT, TIMEOUT_AFTER_COMMIT -> {
                // The baseline seed is sufficient; the HTTP adapter supplies deterministic faults.
            }
        }
    }

    private void seedOrder(boolean pendingCancellation, boolean dispatched) {
        String status = dispatched ? "DISPATCHED" : "PAID";
        Timestamp dispatchedAt = dispatched
                ? Timestamp.from(Instant.parse("2026-01-10T10:05:00Z"))
                : null;
        jdbc.update("""
                INSERT INTO customer_order
                    (id, customer_id, status, cancellation_status, paid_total, active_total, currency,
                     payment_method, payment_reference, recipient_name, address_line1, address_line2,
                     city, state, postal_code, country_code, phone, store_id, hub_id, dispatched_at,
                     created_at, updated_at, version)
                VALUES ('demo-order-001', 'customer-001', ?, 'NONE', 2599000, 2599000, 'ARS',
                        'CARD', 'demo-payment-001', 'Cliente Demo', 'Av. Corrientes 1234', 'Piso 4',
                        'Buenos Aires', 'CABA', 'C1043', 'AR', '+54 11 5555 0101', 'store-001',
                        'hub-001', ?, '2026-01-10T10:00:00Z',
                        '2026-01-10T10:05:00Z', 0)
                """, status, dispatchedAt);
        jdbc.update("""
                INSERT INTO order_item
                    (id, order_id, product_id, product_name, sku, quantity, unit_price, active_amount,
                     currency, status, store_id, hub_id, version)
                VALUES ('demo-order-item-001', 'demo-order-001', 'product-001', 'Camisa clásica',
                        'TZ-CAM-001', 1, 2599000, 2599000, 'ARS', ?, 'store-001', 'hub-001', 0)
                """, status);
        jdbc.update("UPDATE product SET stock = 24, version = 1 WHERE id = 'product-001'");

        if (pendingCancellation) {
            jdbc.update("""
                    INSERT INTO cancellation_request
                        (id, order_id, status, reason_code, reason, requested_by_type, requested_by_id,
                         requested_at, updated_at, version, expected_order_version)
                    VALUES ('demo-cancellation-001', 'demo-order-001', 'PENDING', 'CUSTOMER_REQUEST',
                            'Escenario de resolución concurrente', 'CUSTOMER', 'customer-001',
                            '2026-01-10T10:10:00Z', '2026-01-10T10:10:00Z', 0, 0)
                    """);
            jdbc.update("""
                    INSERT INTO cancellation_request_item
                        (request_id, order_item_id, quantity, amount, active)
                    VALUES ('demo-cancellation-001', 'demo-order-item-001', 1, 2599000, TRUE)
                    """);
        }
    }

    public record ResetResult(Instant resetAt, int schemaVersion, DemoScenario scenario) {
    }
}
