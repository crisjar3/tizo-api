package com.tizo.ecommerce.operations;

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
class OperationsQueryApiIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void listsOperatorsOrdersAndCancellationDetails() throws Exception {
        Fixture fixture = createPendingCancellation();

        mvc.perform(get("/api/ops/operators").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].active").value(true));

        mvc.perform(get("/api/ops/orders")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .param("sortBy", "createdAt")
                        .param("sortDirection", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(fixture.orderId()))
                .andExpect(jsonPath("$.items[0].customer.email").value("cliente@tizo.local"));

        mvc.perform(get("/api/ops/orders/{orderId}", fixture.orderId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stores[0].id").value("store-001"))
                .andExpect(jsonPath("$.hub.id").value("hub-001"))
                .andExpect(jsonPath("$.activeCancellationRequestId").value(fixture.requestId()))
                .andExpect(jsonPath("$.cancellationEligibility.eligible").value(false));

        mvc.perform(get("/api/ops/cancellation-requests")
                        .param("status", "PENDING")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .param("sortBy", "requestedAt")
                        .param("sortDirection", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(fixture.requestId()))
                .andExpect(jsonPath("$.counts.pending").value(1));

        mvc.perform(get("/api/ops/cancellation-requests/{requestId}", fixture.requestId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedBy.type").value("CUSTOMER"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.audit[0].action").value("REQUEST_CREATED"))
                .andExpect(jsonPath("$.stillValid").value(true));
    }

    @Test
    void validatesOperationalDateRangesAndSorts() throws Exception {
        mvc.perform(get("/api/ops/orders")
                        .param("createdFrom", "2026-08-10T00:00:00Z")
                        .param("createdTo", "2026-08-09T00:00:00Z"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    void exposesCustomerTransitAndDeliveredItemStatesToOperations() throws Exception {
        Fixture fixture = createPendingCancellation();

        jdbc.sql("UPDATE order_item SET status='ON_THE_WAY' WHERE order_id=:orderId")
                .param("orderId", fixture.orderId())
                .update();

        mvc.perform(get("/api/ops/orders/{orderId}", fixture.orderId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("IN_TRANSIT_TO_HUB"));

        jdbc.sql("UPDATE order_item SET status='DELIVERED' WHERE order_id=:orderId")
                .param("orderId", fixture.orderId())
                .update();

        mvc.perform(get("/api/ops/orders/{orderId}", fixture.orderId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("DELIVERED"));
    }

    private Fixture createPendingCancellation() throws Exception {
        mvc.perform(put("/api/me/cart/items/product-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isOk());
        String checkout = mvc.perform(post("/api/me/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"ops-query-checkout-001\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(checkout, "$.order.id");
        String itemId = JsonPath.read(checkout, "$.order.items[0].id");
        String cancellation = mvc.perform(post("/api/me/orders/{orderId}/cancellation-requests", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemIds":["%s"],"reasonCode":"CUSTOMER_REQUEST",
                                 "idempotencyKey":"ops-query-cancel-001","expectedOrderVersion":0}
                                """.formatted(itemId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Fixture(orderId, JsonPath.read(cancellation, "$.requestId"));
    }

    private record Fixture(String orderId, String requestId) {
    }
}
