package com.tizo.ecommerce.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tizo.ecommerce.support.PostgresIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CancellationDecisionPersistenceIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void approvalCommitsInventoryOrderRefundEffectsAndAuditAsOneOutcome() throws Exception {
        mvc.perform(put("/api/me/cart/items/product-004")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isOk());
        String checkout = mvc.perform(post("/api/me/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"persistence-checkout-001\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(checkout, "$.order.id");
        String itemId = JsonPath.read(checkout, "$.order.items[0].id");
        String cancellation = mvc.perform(post("/api/me/orders/{orderId}/cancellation-requests", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemIds":["%s"],"reasonCode":"CUSTOMER_REQUEST",
                                 "idempotencyKey":"persistence-cancel-001","expectedOrderVersion":0}
                                """.formatted(itemId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(cancellation, "$.requestId");

        mvc.perform(post("/api/ops/cancellation-requests/{requestId}/approve", requestId)
                        .header("X-Operator-Id", "op-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"persistence-approve-001","expectedRequestVersion":0,
                                 "expectedOrderVersion":0}
                                """))
                .andExpect(status().isOk());

        Map<String, Object> order = jdbc.sql("""
                        SELECT active_total, cancellation_status, version
                        FROM customer_order WHERE id=:id
                        """).param("id", orderId).query().singleRow();
        assertThat(((Number) order.get("active_total")).longValue()).isZero();
        assertThat(order.get("cancellation_status")).isEqualTo("FULL");
        assertThat(((Number) order.get("version")).longValue()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT status FROM order_item WHERE id=:id")
                .param("id", itemId).query(String.class).single()).isEqualTo("CANCELLED");
        assertThat(jdbc.sql("SELECT stock FROM product WHERE id='product-004'")
                .query(Integer.class).single()).isEqualTo(8);
        assertThat(jdbc.sql("SELECT status FROM refund WHERE cancellation_request_id=:id")
                .param("id", requestId).query(String.class).single()).isEqualTo("PENDING");
        assertThat(jdbc.sql("SELECT count(*) FROM operational_effect WHERE cancellation_request_id=:id")
                .param("id", requestId).query(Long.class).single()).isEqualTo(3);
        assertThat(jdbc.sql("SELECT count(*) FROM audit_event WHERE aggregate_id=:id")
                .param("id", requestId).query(Long.class).single()).isEqualTo(3);
        assertThat(jdbc.sql("SELECT active FROM cancellation_request_item WHERE request_id=:id")
                .param("id", requestId).query(Boolean.class).single()).isFalse();
    }
}
