package com.tizo.ecommerce.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tizo.ecommerce.sales.application.OperationalEffectWorker;
import com.tizo.ecommerce.support.PostgresIntegrationTest;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "tizo.effects.max-attempts=2")
@AutoConfigureMockMvc
class OperationalEffectWorkerIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private OperationalEffectWorker worker;

    @Test
    void completesEffectsAndRecoversExpiredLeases() throws Exception {
        String requestId = approvedCancellation();

        assertThat(worker.runOnce()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM operational_effect WHERE status='COMPLETED'")
                .query(Long.class).single()).isEqualTo(3);
        assertThat(jdbc.sql("SELECT status FROM refund WHERE cancellation_request_id=:id")
                .param("id", requestId).query(String.class).single()).isEqualTo("COMPLETED");

        jdbc.sql("""
                UPDATE operational_effect
                SET status='PROCESSING', lease_until=now()-interval '1 second',
                    next_attempt_at=now()-interval '1 second', payload='{}'::jsonb
                WHERE cancellation_request_id=:id AND effect_type='CUSTOMER_NOTIFICATION'
                """).param("id", requestId).update();
        assertThat(worker.runOnce()).isEqualTo(1);
        assertThat(jdbc.sql("""
                        SELECT status FROM operational_effect
                        WHERE cancellation_request_id=:id AND effect_type='CUSTOMER_NOTIFICATION'
                        """).param("id", requestId).query(String.class).single()).isEqualTo("COMPLETED");
    }

    @Test
    void retriesWithBackoffAndStopsAtMaximumAttempts() throws Exception {
        String requestId = approvedCancellation();
        jdbc.sql("""
                UPDATE operational_effect
                SET payload='{"simulateFailure":true}'::jsonb
                WHERE cancellation_request_id=:id AND effect_type='REFUND'
                """).param("id", requestId).update();

        worker.runOnce();
        Map<String, Object> first = jdbc.sql("""
                        SELECT status, attempts, next_attempt_at
                        FROM operational_effect
                        WHERE cancellation_request_id=:id AND effect_type='REFUND'
                        """).param("id", requestId).query().singleRow();
        assertThat(first.get("status")).isEqualTo("PENDING");
        assertThat(((Number) first.get("attempts")).intValue()).isEqualTo(1);
        OffsetDateTime nextAttemptAt = jdbc.sql("""
                        SELECT next_attempt_at
                        FROM operational_effect
                        WHERE cancellation_request_id=:id AND effect_type='REFUND'
                        """)
                .param("id", requestId)
                .query((row, number) -> row.getObject("next_attempt_at", OffsetDateTime.class))
                .single();
        assertThat(nextAttemptAt).isAfter(OffsetDateTime.now().minusSeconds(1));

        jdbc.sql("""
                UPDATE operational_effect SET next_attempt_at=now()-interval '1 second'
                WHERE cancellation_request_id=:id AND effect_type='REFUND'
                """).param("id", requestId).update();
        worker.runOnce();
        assertThat(jdbc.sql("""
                        SELECT status FROM operational_effect
                        WHERE cancellation_request_id=:id AND effect_type='REFUND'
                        """).param("id", requestId).query(String.class).single()).isEqualTo("FAILED");
    }

    private String approvedCancellation() throws Exception {
        mvc.perform(put("/api/me/cart/items/product-004")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isOk());
        String checkout = mvc.perform(post("/api/me/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"worker-checkout-0001\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(checkout, "$.order.id");
        String itemId = JsonPath.read(checkout, "$.order.items[0].id");
        String cancellation = mvc.perform(post("/api/me/orders/{id}/cancellation-requests", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemIds":["%s"],"reasonCode":"CUSTOMER_REQUEST",
                                 "idempotencyKey":"worker-cancel-0001","expectedOrderVersion":0}
                                """.formatted(itemId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(cancellation, "$.requestId");
        mvc.perform(post("/api/ops/cancellation-requests/{id}/approve", requestId)
                        .header("X-Operator-Id", "op-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"worker-approve-0001",
                                 "expectedRequestVersion":0,"expectedOrderVersion":0}
                                """))
                .andExpect(status().isOk());
        return requestId;
    }
}
