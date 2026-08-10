package com.tizo.ecommerce.e2e;

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
class EcommerceJourneyIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void completesCatalogCartCheckoutCancellationDecisionAndHistory() throws Exception {
        mvc.perform(post("/api/mock/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"normal\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/catalog/products/product-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
        mvc.perform(put("/api/me/cart/items/product-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2));

        String checkout = mvc.perform(post("/api/me/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"e2e-checkout-0001\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(true))
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(checkout, "$.order.id");
        String itemId = JsonPath.read(checkout, "$.order.items[0].id");
        Integer orderVersion = JsonPath.read(checkout, "$.order.version");

        String receipt = mvc.perform(post("/api/me/orders/{orderId}/cancellation-requests", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemIds":["%s"],"reasonCode":"CUSTOMER_REQUEST",
                                 "reasonNote":"E2E journey","idempotencyKey":"e2e-cancel-0001",
                                 "expectedOrderVersion":%d}
                                """.formatted(itemId, orderVersion)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(receipt, "$.requestId");

        String detail = mvc.perform(get("/api/ops/cancellation-requests/{requestId}", requestId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer requestVersion = JsonPath.read(detail, "$.version");
        Integer currentOrderVersion = JsonPath.read(detail, "$.currentOrderVersion");

        mvc.perform(post("/api/ops/cancellation-requests/{requestId}/approve", requestId)
                        .header("X-Operator-Id", "op-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"e2e-approve-0001","expectedRequestVersion":%d,
                                 "expectedOrderVersion":%d,"note":"E2E approval"}
                                """.formatted(requestVersion, currentOrderVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.status").value("COMPLETED"))
                .andExpect(jsonPath("$.request.effects.length()").value(3))
                .andExpect(jsonPath("$.order.cancellationStatus").value("FULL"));

        mvc.perform(get("/api/ops/cancellation-history")
                        .param("search", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].requestId").value(requestId))
                .andExpect(jsonPath("$.items[0].refundStatus").value("PENDING"));
    }
}
