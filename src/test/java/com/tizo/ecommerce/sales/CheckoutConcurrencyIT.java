package com.tizo.ecommerce.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tizo.ecommerce.support.PostgresIntegrationTest;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class CheckoutConcurrencyIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void serializesCompetingCheckoutsAndReplaysTheWinningSnapshotExactly() throws Exception {
        mvc.perform(put("/api/me/cart/items/product-004")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isOk());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CompletableFuture<CheckoutResponse> first = checkoutAsync(
                    executor, ready, start, "checkout-race-0001");
            CompletableFuture<CheckoutResponse> second = checkoutAsync(
                    executor, ready, start, "checkout-race-0002");
            ready.await();
            start.countDown();

            List<CheckoutResponse> responses = List.of(first.join(), second.join());
            assertThat(responses).extracting(CheckoutResponse::status).containsExactlyInAnyOrder(201, 422);

            CheckoutResponse winner = responses.stream()
                    .filter(response -> response.status() == 201)
                    .findFirst()
                    .orElseThrow();
            String replay = mvc.perform(post("/api/me/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"idempotencyKey\":\"" + winner.key() + "\"}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            assertThat(replay).isEqualTo(winner.body());
            assertThat(jdbc.sql("SELECT count(*) FROM customer_order").query(Long.class).single())
                    .isEqualTo(1);
            assertThat(jdbc.sql("SELECT stock FROM product WHERE id='product-004'")
                    .query(Integer.class).single()).isEqualTo(6);
        }
    }

    private CompletableFuture<CheckoutResponse> checkoutAsync(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            String key) {
        return CompletableFuture.supplyAsync(() -> {
            ready.countDown();
            try {
                start.await();
                MvcResult result = mvc.perform(post("/api/me/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"idempotencyKey\":\"" + key + "\"}"))
                        .andReturn();
                return new CheckoutResponse(
                        key, result.getResponse().getStatus(), result.getResponse().getContentAsString());
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }, executor);
    }

    private record CheckoutResponse(String key, int status, String body) {
    }
}
