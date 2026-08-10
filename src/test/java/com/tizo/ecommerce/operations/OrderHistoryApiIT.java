package com.tizo.ecommerce.operations;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OrderHistoryApiIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void exposesTerminalHistoryAndSanitizedAuditAndEffects() throws Exception {
        String requestId = approveCancellation();

        mvc.perform(get("/api/ops/cancellation-history")
                        .param("status", "COMPLETED")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .param("sortBy", "resolvedAt")
                        .param("sortDirection", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].requestId").value(requestId))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].refundStatus").value("PENDING"));

        mvc.perform(get("/api/ops/cancellation-requests/{id}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audit[0].action").value("REQUEST_CREATED"))
                .andExpect(jsonPath("$.audit[1].action").value("REQUEST_APPROVED"))
                .andExpect(jsonPath("$.audit[2].action").value("CANCELLATION_COMPLETED"))
                .andExpect(jsonPath("$.effects[0]", not(hasKey("payload"))))
                .andExpect(jsonPath("$.effects[0]", not(hasKey("lastError"))))
                .andExpect(jsonPath("$", not(hasKey("paymentReference"))));
    }

    private String approveCancellation() throws Exception {
        mvc.perform(put("/api/me/cart/items/product-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isOk());
        String checkout = mvc.perform(post("/api/me/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"history-checkout-0001\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(checkout, "$.order.id");
        String itemId = JsonPath.read(checkout, "$.order.items[0].id");
        String cancellation = mvc.perform(post("/api/me/orders/{id}/cancellation-requests", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemIds":["%s"],"reasonCode":"CUSTOMER_REQUEST",
                                 "idempotencyKey":"history-cancel-0001","expectedOrderVersion":0}
                                """.formatted(itemId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(cancellation, "$.requestId");
        mvc.perform(post("/api/ops/cancellation-requests/{id}/approve", requestId)
                        .header("X-Operator-Id", "op-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"history-approve-0001",
                                 "expectedRequestVersion":0,"expectedOrderVersion":0}
                                """))
                .andExpect(status().isOk());
        return requestId;
    }
}
