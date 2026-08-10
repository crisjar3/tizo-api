package com.tizo.ecommerce.catalog.adapter.out.persistence;

import com.tizo.ecommerce.catalog.application.CatalogPort;
import com.tizo.ecommerce.catalog.domain.Product;
import com.tizo.ecommerce.shared.money.Money;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JpaCatalogAdapter implements CatalogPort {

    private final ProductJpaRepository products;
    private final JdbcClient jdbc;

    public JpaCatalogAdapter(ProductJpaRepository products, JdbcClient jdbc) {
        this.products = products;
        this.jdbc = jdbc;
    }

    @Override
    public ProductPage findProducts(ProductSearch search) {
        Specification<ProductEntity> specification = (root, query, builder) -> builder.isTrue(root.get("active"));
        if (search.search() != null) {
            String pattern = "%" + search.search().toLowerCase() + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("name")), pattern),
                    builder.like(builder.lower(root.get("description")), pattern)));
        }
        if (search.category() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(builder.lower(root.get("category")), search.category().toLowerCase()));
        }
        Sort sort = Sort.by(search.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC, search.sortBy());
        Page<ProductEntity> page = products.findAll(
                specification,
                PageRequest.of(search.page() - 1, search.pageSize(), sort));
        List<String> ids = page.getContent().stream().map(ProductEntity::id).toList();
        Map<String, List<String>> images = images(ids);
        Map<String, List<Product.Attribute>> attributes = attributes(ids);
        List<Product> result = page.getContent().stream()
                .map(entity -> map(entity, images.get(entity.id()), attributes.get(entity.id())))
                .toList();
        return new ProductPage(result, search.page(), search.pageSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Override
    public Optional<Product> findById(String productId) {
        return products.findById(productId).map(entity -> map(
                entity,
                images(List.of(productId)).get(productId),
                attributes(List.of(productId)).get(productId)));
    }

    private Product map(ProductEntity entity, List<String> images, List<Product.Attribute> attributes) {
        return new Product(
                entity.id(), entity.sku(), entity.name(), entity.description(), entity.category(),
                new Money(entity.priceAmount(), entity.currency()), entity.stock(), entity.active(),
                entity.createdAt(), entity.version(), images, attributes);
    }

    private Map<String, List<String>> images(List<String> productIds) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        productIds.forEach(id -> result.put(id, new ArrayList<>()));
        if (productIds.isEmpty()) {
            return result;
        }
        jdbc.sql("""
                        SELECT product_id, url
                        FROM product_image
                        WHERE product_id IN (:ids)
                        ORDER BY product_id, display_order
                        """)
                .param("ids", productIds)
                .query((row, number) -> Map.entry(row.getString("product_id"), row.getString("url")))
                .list()
                .forEach(entry -> result.get(entry.getKey()).add(entry.getValue()));
        return result;
    }

    private Map<String, List<Product.Attribute>> attributes(List<String> productIds) {
        Map<String, List<Product.Attribute>> result = new LinkedHashMap<>();
        productIds.forEach(id -> result.put(id, new ArrayList<>()));
        if (productIds.isEmpty()) {
            return result;
        }
        jdbc.sql("""
                        SELECT product_id, name, value
                        FROM product_attribute
                        WHERE product_id IN (:ids)
                        ORDER BY product_id, name
                        """)
                .param("ids", productIds)
                .query((row, number) -> new AttributeRow(
                        row.getString("product_id"), row.getString("name"), row.getString("value")))
                .list()
                .forEach(row -> result.get(row.productId()).add(new Product.Attribute(row.name(), row.value())));
        return result;
    }

    private record AttributeRow(String productId, String name, String value) {
    }
}
