package com.tizo.ecommerce.catalog.adapter.in.web;

import com.tizo.ecommerce.catalog.application.CatalogService;
import com.tizo.ecommerce.generated.api.CatalogApi;
import com.tizo.ecommerce.generated.model.ProductDetail;
import com.tizo.ecommerce.generated.model.ProductListResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogController implements CatalogApi {

    private final CatalogService catalog;
    private final CatalogWebMapper mapper;

    public CatalogController(CatalogService catalog, CatalogWebMapper mapper) {
        this.catalog = catalog;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<ProductDetail> getProduct(String productId) {
        return ResponseEntity.ok(mapper.toDetail(catalog.get(productId)));
    }

    @Override
    public ResponseEntity<ProductListResponse> listProducts(
            String search,
            String category,
            Integer page,
            Integer pageSize,
            String sortBy,
            String sortDirection) {
        return ResponseEntity.ok(mapper.toResponse(
                catalog.list(search, category, page, pageSize, sortBy, sortDirection)));
    }
}
