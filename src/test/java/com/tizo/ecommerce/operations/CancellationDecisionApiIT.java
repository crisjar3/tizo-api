package com.tizo.ecommerce.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tizo.ecommerce.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CancellationDecisionApiIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void activeOperatorApprovesAtomicallyAndReplayIsExact() throws Exception {
        Fixture fixture = createPending("product-002", 2, "decision-approve");
        String payload = """
                {"idempotencyKey":"approve-decision-0001","expectedRequestVersion":0,
                 "expectedOrderVersion":0,"note":"Aprobación verificada"}
                """;
        String first = mvc.perform(post("/api/ops/cancellation-requests/{requestId}/approve", fixture.requestId())
                        .header("X-Operator-Id", "op-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.status").value("COMPLETED"))
                .andExpect(jsonPath("$.order.cancellationStatus").value("FULL"))
                .andExpect(jsonPath("$.request.refund.status").value("PENDING"))
                .andExpect(jsonPath("$.request.effects.length()").value(3))
                .andReturn().getResponse().getContentAsString();

        String replay = mvc.perform(post("/api/ops/cancellation-requests/{requestId}/approve", fixture.requestId())
                        .header("X-Operator-Id", "op-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(replay).isEqualTo(first);

        mvc.perform(get("/api/ops/cancellation-requests/by-idempotency-key/approve-decision-0001")
                        .param("scope", "APPROVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.request.id").value(fixture.requestId()));

        assertThat(jdbc.sql("SELECT stock FROM product WHERE id='product-002'")
                .query(Integer.class).single()).isEqualTo(18);
    }

    @Test
    void rejectsWithoutChangingInventoryAndBlocksInvalidOperators() throws Exception {
        Fixture fixture = createPending("product-003", 1, "decision-reject");
        mvc.perform(post("/api/ops/cancellation-requests/{requestId}/reject", fixture.requestId())
                        .header("X-Operator-Id", "op-inactive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"reject-inactive-001","expectedRequestVersion":0,
                                 "rejectionCode":"OTHER"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OPERATOR_INACTIVE"));

        mvc.perform(post("/api/ops/cancellation-requests/{requestId}/reject", fixture.requestId())
                        .header("X-Operator-Id", "op-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"reject-decision-001","expectedRequestVersion":0,
                                 "rejectionCode":"OTHER","rejectionNote":"No corresponde"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.status").value("REJECTED"))
                .andExpect(jsonPath("$.order.cancellationStatus").value("NONE"));

        assertThat(jdbc.sql("SELECT stock FROM product WHERE id='product-003'")
                .query(Integer.class).single()).isEqualTo(11);
        assertThat(jdbc.sql("SELECT count(*) FROM refund").query(Long.class).single()).isZero();
    }

    @Test
    void activeOperatorCanCreateAnOperationalCancellation() throws Exception {
        mvc.perform(put("/api/me/cart/items/product-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isOk());
        String checkout = mvc.perform(post("/api/me/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"ops-create-checkout-001\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(checkout, "$.order.id");
        String itemId = JsonPath.read(checkout, "$.order.items[0].id");

        mvc.perform(post("/api/ops/cancellation-requests")
                        .header("X-Operator-Id", "op-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":"%s","itemIds":["%s"],"reasonCode":"OUT_OF_STOCK",
                                 "idempotencyKey":"ops-create-cancel-001","expectedOrderVersion":0}
                                """.formatted(orderId, itemId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.request.requestedBy.type").value("OPERATOR"))
                .andExpect(jsonPath("$.request.requestedBy.id").value("op-001"))
                .andExpect(jsonPath("$.created").value(true));
    }

    private Fixture createPending(String productId, int quantity, String keyPrefix) throws Exception {
        mvc.perform(put("/api/me/cart/items/{productId}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
        String checkout = mvc.perform(post("/api/me/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"" + keyPrefix + "-checkout\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(checkout, "$.order.id");
        String itemId = JsonPath.read(checkout, "$.order.items[0].id");
        String cancellation = mvc.perform(post("/api/me/orders/{orderId}/cancellation-requests", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemIds":["%s"],"reasonCode":"CUSTOMER_REQUEST",
                                 "idempotencyKey":"%s-cancel","expectedOrderVersion":0}
                                """.formatted(itemId, keyPrefix)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Fixture(orderId, JsonPath.read(cancellation, "$.requestId"));
    }

    private record Fixture(String orderId, String requestId) {
    }
}
