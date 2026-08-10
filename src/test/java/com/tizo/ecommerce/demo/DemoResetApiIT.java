package com.tizo.ecommerce.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tizo.ecommerce.support.PostgresIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "tizo.demo.slow-delay=75ms")
@AutoConfigureMockMvc
class DemoResetApiIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void normalResetIsDeterministic() throws Exception {
        reset("normal");
        List<Map<String, Object>> first = productSnapshot();

        jdbc.sql("UPDATE product SET stock = 1, active = FALSE").update();
        jdbc.sql("INSERT INTO cart_item VALUES ('cart-customer-001', 'product-001', 4, now(), now())").update();

        reset("normal");
        assertThat(productSnapshot()).isEqualTo(first);
        assertThat(first).hasSize(5);
        assertThat(jdbc.sql("SELECT count(*) FROM cart_item").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM operator_account WHERE active").query(Long.class).single())
                .isEqualTo(2);
    }

    @Test
    void dataScenariosAreRepeatable() throws Exception {
        reset("empty");
        mvc.perform(get("/api/catalog/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));

        reset("concurrent-resolution");
        assertThat(scenarioSnapshot()).containsExactly(1L, 1L, 0L);
        reset("concurrent-resolution");
        assertThat(scenarioSnapshot()).containsExactly(1L, 1L, 0L);

        reset("dispatched-order");
        assertThat(jdbc.sql("SELECT count(*) FROM customer_order WHERE status='DISPATCHED' AND dispatched_at IS NOT NULL")
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void faultScenariosAreDeterministicAndPreserveReconciliation() throws Exception {
        reset("slow");
        long started = System.nanoTime();
        mvc.perform(get("/api/catalog/products")).andExpect(status().isOk());
        assertThat((System.nanoTime() - started) / 1_000_000).isGreaterThanOrEqualTo(60);

        reset("server-error");
        mvc.perform(get("/api/catalog/products"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("DEMO_SERVER_ERROR"));

        reset("timeout-before-commit");
        mvc.perform(put("/api/me/cart/items/product-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("DEMO_TIMEOUT_BEFORE_COMMIT"));
        assertThat(jdbc.sql("SELECT count(*) FROM cart_item").query(Long.class).single()).isZero();

        reset("timeout-after-commit");
        mvc.perform(put("/api/me/cart/items/product-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isGatewayTimeout());
        mvc.perform(post("/api/me/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"demo-timeout-checkout-001\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("DEMO_TIMEOUT_AFTER_COMMIT"));
        mvc.perform(get("/api/me/orders/by-idempotency-key/demo-timeout-checkout-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.order.id").isNotEmpty());
    }

    private void reset(String scenario) throws Exception {
        mvc.perform(post("/api/mock/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"" + scenario + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value(2))
                .andExpect(jsonPath("$.scenario").value(scenario));
    }

    private List<Map<String, Object>> productSnapshot() {
        return jdbc.sql("SELECT id, stock, active, version FROM product ORDER BY id")
                .query().listOfRows();
    }

    private List<Long> scenarioSnapshot() {
        return List.of(
                jdbc.sql("SELECT count(*) FROM customer_order WHERE id='demo-order-001'")
                        .query(Long.class).single(),
                jdbc.sql("SELECT count(*) FROM cancellation_request WHERE id='demo-cancellation-001' AND status='PENDING'")
                        .query(Long.class).single(),
                jdbc.sql("SELECT count(*) FROM refund").query(Long.class).single());
    }
}
