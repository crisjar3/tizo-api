package com.tizo.ecommerce.catalog.application;

import com.tizo.ecommerce.catalog.domain.Product;
import com.tizo.ecommerce.shared.error.DomainException;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "name", "name",
            "price", "priceAmount",
            "createdAt", "createdAt");

    private final CatalogPort catalog;

    public CatalogService(CatalogPort catalog) {
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public CatalogPort.ProductPage list(
            String search,
            String category,
            int page,
            int pageSize,
            String sortBy,
            String sortDirection) {
        String persistenceSort = SORT_FIELDS.get(sortBy);
        if (persistenceSort == null) {
            throw DomainException.validation("INVALID_SORT_FIELD", "El campo de ordenamiento no está permitido.");
        }
        if (!"asc".equalsIgnoreCase(sortDirection) && !"desc".equalsIgnoreCase(sortDirection)) {
            throw DomainException.validation("INVALID_SORT_DIRECTION", "La dirección debe ser asc o desc.");
        }
        return catalog.findProducts(new CatalogPort.ProductSearch(
                normalize(search),
                normalize(category),
                page,
                pageSize,
                persistenceSort,
                "asc".equalsIgnoreCase(sortDirection)));
    }

    @Transactional(readOnly = true)
    public Product get(String productId) {
        return catalog.findById(productId)
                .filter(Product::active)
                .orElseThrow(() -> DomainException.notFound("PRODUCT_NOT_FOUND", "El producto no existe."));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
