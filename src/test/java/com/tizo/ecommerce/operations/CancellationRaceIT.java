package com.tizo.ecommerce.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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

@SpringBootTest
@AutoConfigureMockMvc
class CancellationRaceIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void exactlyOneCompetingDecisionWins() throws Exception {
        String requestId = pendingCancellation();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CompletableFuture<Integer> approve = callAsync(executor, ready, start, () -> mvc.perform(
                            post("/api/ops/cancellation-requests/{id}/approve", requestId)
                                    .header("X-Operator-Id", "op-001")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"idempotencyKey":"race-approve-0001",
                                             "expectedRequestVersion":0,"expectedOrderVersion":0}
                                            """))
                    .andReturn().getResponse().getStatus());
            CompletableFuture<Integer> reject = callAsync(executor, ready, start, () -> mvc.perform(
                            post("/api/ops/cancellation-requests/{id}/reject", requestId)
                                    .header("X-Operator-Id", "op-002")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"idempotencyKey":"race-reject-0001",
                                             "expectedRequestVersion":0,"rejectionCode":"OTHER"}
                                            """))
                    .andReturn().getResponse().getStatus());
            ready.await();
            start.countDown();

            assertThat(List.of(approve.join(), reject.join())).containsExactlyInAnyOrder(200, 409);
        }

        String terminalStatus = jdbc.sql("SELECT status FROM cancellation_request WHERE id=:id")
                .param("id", requestId).query(String.class).single();
        assertThat(terminalStatus).isIn("COMPLETED", "REJECTED");
        assertThat(jdbc.sql("SELECT count(*) FROM idempotent_operation WHERE scope IN ('OPS_APPROVE','OPS_REJECT')")
                .query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM audit_event WHERE aggregate_id=:id AND action IN ('REQUEST_APPROVED','REQUEST_REJECTED')")
                .param("id", requestId).query(Long.class).single()).isEqualTo(1);
    }

    private CompletableFuture<Integer> callAsync(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            ThrowingIntSupplier call) {
        return CompletableFuture.supplyAsync(() -> {
            ready.countDown();
            try {
                start.await();
                return call.getAsInt();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }, executor);
    }

    private String pendingCancellation() throws Exception {
        mvc.perform(put("/api/me/cart/items/product-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isOk());
        String checkout = mvc.perform(post("/api/me/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"race-checkout-0001\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(checkout, "$.order.id");
        String itemId = JsonPath.read(checkout, "$.order.items[0].id");
        String cancellation = mvc.perform(post("/api/me/orders/{id}/cancellation-requests", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemIds":["%s"],"reasonCode":"CUSTOMER_REQUEST",
                                 "idempotencyKey":"race-cancel-0001","expectedOrderVersion":0}
                                """.formatted(itemId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(cancellation, "$.requestId");
    }

    @FunctionalInterface
    private interface ThrowingIntSupplier {
        int getAsInt() throws Exception;
    }
}
