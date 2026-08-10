package com.tizo.ecommerce.catalog.adapter.in.web;

import com.tizo.ecommerce.catalog.application.CatalogPort;
import com.tizo.ecommerce.catalog.domain.Product;
import com.tizo.ecommerce.generated.model.CurrencyCode;
import com.tizo.ecommerce.generated.model.Money;
import com.tizo.ecommerce.generated.model.Pagination;
import com.tizo.ecommerce.generated.model.ProductAttribute;
import com.tizo.ecommerce.generated.model.ProductDetail;
import com.tizo.ecommerce.generated.model.ProductListResponse;
import com.tizo.ecommerce.generated.model.ProductSummary;
import org.springframework.stereotype.Component;

@Component
public class CatalogWebMapper {

    public ProductListResponse toResponse(CatalogPort.ProductPage page) {
        return new ProductListResponse(
                page.items().stream().map(this::toSummary).toList(),
                new Pagination(page.page(), page.pageSize(), page.totalItems(), page.totalPages()));
    }

    public ProductDetail toDetail(Product product) {
        return new ProductDetail()
                .id(product.id())
                .name(product.name())
                .description(product.description())
                .category(product.category())
                .imageUrl(product.primaryImageUrl())
                .price(toMoney(product.price()))
                .availableStock(product.availableStock())
                .available(product.available())
                .longDescription(product.description())
                .imageUrls(product.imageUrls())
                .attributes(product.attributes().stream()
                        .map(attribute -> new ProductAttribute(attribute.name(), attribute.value()))
                        .toList());
    }

    private ProductSummary toSummary(Product product) {
        return new ProductSummary(
                product.id(),
                product.name(),
                product.description(),
                product.category(),
                product.primaryImageUrl(),
                toMoney(product.price()),
                product.availableStock(),
                product.available());
    }

    private Money toMoney(com.tizo.ecommerce.shared.money.Money money) {
        return new Money(money.amount(), CurrencyCode.ARS);
    }
}
