package com.tizo.ecommerce.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class CheckoutApiIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void atomicallyCreatesOrderDecrementsStockEmptiesCartAndReplays() throws Exception {
        putProduct("product-001", 2);

        String first = mvc.perform(post("/api/me/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"checkout-0001\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/me/orders/.+")))
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.order.paidTotal.amountMinor").value(5_198_000))
                .andExpect(jsonPath("$.order.items[0].customerStatus").value("CONFIRMED"))
                .andReturn().getResponse().getContentAsString();

        String replay = mvc.perform(post("/api/me/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"checkout-0001\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(replay).isEqualTo(first);
        assertThat(jdbc.sql("SELECT count(*) FROM customer_order").query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT stock FROM product WHERE id='product-001'").query(Integer.class).single())
                .isEqualTo(23);
        assertThat(jdbc.sql("SELECT count(*) FROM cart_item").query(Long.class).single()).isZero();
    }

    @Test
    void reconcilesCommittedCheckoutWithoutReexecutingIt() throws Exception {
        putProduct("product-002", 1);
        mvc.perform(post("/api/me/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"checkout-reconcile-001\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/me/orders/by-idempotency-key/checkout-reconcile-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.idempotencyKey").value("checkout-reconcile-001"))
                .andExpect(jsonPath("$.order.id").isString());
    }

    @Test
    void rollsBackWhenStockChangedAfterCartUpdate() throws Exception {
        putProduct("product-004", 2);
        jdbc.sql("UPDATE product SET stock=1 WHERE id='product-004'").update();

        mvc.perform(post("/api/me/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"checkout-stock-001\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertThat(jdbc.sql("SELECT count(*) FROM customer_order").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT quantity FROM cart_item WHERE product_id='product-004'")
                .query(Integer.class).single()).isEqualTo(2);
    }

    @Test
    void rejectsEmptyCart() throws Exception {
        mvc.perform(post("/api/me/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"checkout-empty-001\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CART_EMPTY"));
    }

    private void putProduct(String productId, int quantity) throws Exception {
        mvc.perform(put("/api/me/cart/items/{productId}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }
}
