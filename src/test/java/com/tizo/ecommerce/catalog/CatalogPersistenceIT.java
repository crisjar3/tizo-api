package com.tizo.ecommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tizo.ecommerce.catalog.application.CatalogPort;
import com.tizo.ecommerce.catalog.application.CatalogService;
import com.tizo.ecommerce.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class CatalogPersistenceIT extends PostgresIntegrationTest {

    @Autowired
    private CatalogService catalog;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void returnsStablePagesWithImagesAndAttributesLoadedInBatches() {
        CatalogPort.ProductPage page = catalog.list(null, null, 1, 2, "createdAt", "asc");

        assertThat(page.totalItems()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.items()).extracting("id").containsExactly("product-001", "product-002");
        assertThat(page.items().getFirst().imageUrls()).isNotEmpty();
        assertThat(page.items().getFirst().attributes()).isNotEmpty();
    }

    @Test
    void databaseRejectsNegativeStockAsLastDefense() {
        assertThatThrownBy(() -> jdbc.sql("UPDATE product SET stock = -1 WHERE id = 'product-001'").update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
