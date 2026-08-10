package com.tizo.ecommerce.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class CustomerCancellationApiIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void createsReplaysAndReconcilesPendingCancellationWithFullLineQuantity() throws Exception {
        OrderFixture order = checkout("product-001", 2, "checkout-cancel-001");
        String request = cancellationJson(order, "cancel-request-0001", "CUSTOMER_REQUEST", 0);

        String first = mvc.perform(post("/api/me/orders/{orderId}/cancellation-requests", order.orderId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/me/orders/" + order.orderId()))
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.itemIds[0]").value(order.itemId()))
                .andExpect(jsonPath("$.affectedAmount.amountMinor").value(5_198_000))
                .andReturn().getResponse().getContentAsString();

        String replay = mvc.perform(post("/api/me/orders/{orderId}/cancellation-requests", order.orderId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(replay).isEqualTo(first);
        String requestId = JsonPath.read(first, "$.requestId");

        mvc.perform(get("/api/me/cancellation-requests/by-idempotency-key/cancel-request-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.request.requestId").value(requestId));

        assertThat(jdbc.sql("SELECT quantity FROM cancellation_request_item")
                .query(Integer.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM audit_event WHERE action='CANCELLATION_REQUESTED'")
                .query(Long.class).single()).isEqualTo(1);

        mvc.perform(get("/api/me/orders/{orderId}", order.orderId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellation.status").value("PENDING"))
                .andExpect(jsonPath("$.cancellation.refund.status").value("NOT_REQUIRED"));
    }

    @Test
    void rejectsStaleUnknownDispatchedAndAlreadyRequestedLines() throws Exception {
        OrderFixture stale = checkout("product-002", 1, "checkout-cancel-002");
        mvc.perform(post("/api/me/orders/{orderId}/cancellation-requests", stale.orderId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancellationJson(stale, "cancel-stale-0001", "CUSTOMER_REQUEST", 8)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_ORDER_VERSION"));

        mvc.perform(post("/api/me/orders/{orderId}/cancellation-requests", stale.orderId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemIds":["not-an-order-item"],"reasonCode":"CUSTOMER_REQUEST",
                                 "idempotencyKey":"cancel-unknown-001","expectedOrderVersion":0}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CANCELLATION_ITEM_NOT_FOUND"));

        String valid = cancellationJson(stale, "cancel-active-0001", "CUSTOMER_REQUEST", 0);
        mvc.perform(post("/api/me/orders/{orderId}/cancellation-requests", stale.orderId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/me/orders/{orderId}/cancellation-requests", stale.orderId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancellationJson(stale, "cancel-active-0002", "OTHER", 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_CANCELLATION_EXISTS"));

        OrderFixture dispatched = checkout("product-003", 1, "checkout-cancel-003");
        jdbc.sql("""
                UPDATE customer_order
                SET status='DISPATCHED', dispatched_at=now(), version=version+1
                WHERE id=:id
                """).param("id", dispatched.orderId()).update();
        mvc.perform(post("/api/me/orders/{orderId}/cancellation-requests", dispatched.orderId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancellationJson(dispatched, "cancel-shipped-001", "CUSTOMER_REQUEST", 1)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ORDER_ALREADY_DISPATCHED"));
    }

    @Test
    void detectsIdempotencyKeyReusedWithDifferentIntent() throws Exception {
        OrderFixture order = checkout("product-004", 1, "checkout-cancel-004");
        mvc.perform(post("/api/me/orders/{orderId}/cancellation-requests", order.orderId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancellationJson(order, "cancel-reused-0001", "CUSTOMER_REQUEST", 0)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/me/orders/{orderId}/cancellation-requests", order.orderId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancellationJson(order, "cancel-reused-0001", "OTHER", 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    private OrderFixture checkout(String productId, int quantity, String key) throws Exception {
        mvc.perform(put("/api/me/cart/items/{productId}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
        String body = mvc.perform(post("/api/me/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"" + key + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new OrderFixture(JsonPath.read(body, "$.order.id"), JsonPath.read(body, "$.order.items[0].id"));
    }

    private String cancellationJson(OrderFixture order, String key, String reasonCode, long version) {
        return """
                {"itemIds":["%s"],"reasonCode":"%s","reasonNote":"Solicitud del cliente",
                 "idempotencyKey":"%s","expectedOrderVersion":%d}
                """.formatted(order.itemId(), reasonCode, key, version);
    }

    private record OrderFixture(String orderId, String itemId) {
    }
}
