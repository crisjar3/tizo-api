package com.tizo.ecommerce.sales;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tizo.ecommerce.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerOrdersApiIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void listsAndReadsOwnOrderWithoutOperationalFields() throws Exception {
        mvc.perform(put("/api/me/cart/items/product-003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isOk());
        String orderId = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(post("/api/me/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"idempotencyKey\":\"checkout-query-001\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.order.id");

        mvc.perform(get("/api/me/orders")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .param("sortBy", "createdAt")
                        .param("sortDirection", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(orderId));

        mvc.perform(get("/api/me/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryAddress.city").value("Buenos Aires"))
                .andExpect(jsonPath("$.items[0].productName").value("Mochila diaria"))
                .andExpect(jsonPath("$", not(hasKey("operator"))))
                .andExpect(jsonPath("$", not(hasKey("store"))))
                .andExpect(jsonPath("$", not(hasKey("hub"))))
                .andExpect(jsonPath("$", not(hasKey("dispatchedAt"))));
    }

    @Test
    void hidesUnknownOrForeignOrder() throws Exception {
        mvc.perform(get("/api/me/orders/order-not-owned"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }
}
