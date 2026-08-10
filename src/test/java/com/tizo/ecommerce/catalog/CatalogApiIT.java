package com.tizo.ecommerce.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tizo.ecommerce.shared.observability.CorrelationIdFilter;
import com.tizo.ecommerce.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogApiIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void listsFiltersSortsAndPaginatesProducts() throws Exception {
        mvc.perform(get("/api/catalog/products")
                        .param("category", "accesorios")
                        .param("search", "mochila")
                        .param("page", "1")
                        .param("pageSize", "10")
                        .param("sortBy", "name")
                        .param("sortDirection", "asc"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CorrelationIdFilter.HEADER))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value("product-003"))
                .andExpect(jsonPath("$.items[0].price.amountMinor").value(3_199_000))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.totalItems").value(1));
    }

    @Test
    void returnsProductDetailWithImagesAndAttributes() throws Exception {
        mvc.perform(get("/api/catalog/products/product-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("product-001"))
                .andExpect(jsonPath("$.imageUrls[0]").isString())
                .andExpect(jsonPath("$.attributes[0].name").value("material"))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void returnsNotFoundForUnknownProduct() throws Exception {
        mvc.perform(get("/api/catalog/products/missing-product"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }
}
