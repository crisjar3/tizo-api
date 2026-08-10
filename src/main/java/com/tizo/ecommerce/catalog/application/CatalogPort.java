package com.tizo.ecommerce.catalog.application;

import com.tizo.ecommerce.catalog.domain.Product;
import java.util.List;
import java.util.Optional;

public interface CatalogPort {

    ProductPage findProducts(ProductSearch search);

    Optional<Product> findById(String productId);

    record ProductSearch(
            String search,
            String category,
            int page,
            int pageSize,
            String sortBy,
            boolean ascending) {
    }

    record ProductPage(List<Product> items, int page, int pageSize, long totalItems, int totalPages) {
        public ProductPage {
            items = List.copyOf(items);
        }
    }
}
