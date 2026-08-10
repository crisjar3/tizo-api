package com.tizo.ecommerce.sales;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class CartApiIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void startsEmptyAndCalculatesServerSideTotals() throws Exception {
        mvc.perform(get("/api/me/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.subtotal.amountMinor").value(0));

        mvc.perform(put("/api/me/cart/items/product-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].lineTotal.amountMinor").value(5_198_000))
                .andExpect(jsonPath("$.subtotal.amountMinor").value(5_198_000))
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void rejectsQuantityBeyondStock() throws Exception {
        mvc.perform(put("/api/me/cart/items/product-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":26}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void deleteIsIdempotent() throws Exception {
        mvc.perform(delete("/api/me/cart/items/product-001"))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/me/cart/items/product-001"))
                .andExpect(status().isNoContent());
    }
}
