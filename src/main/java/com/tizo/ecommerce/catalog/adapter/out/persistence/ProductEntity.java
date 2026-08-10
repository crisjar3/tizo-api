package com.tizo.ecommerce.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;

@Entity
@Table(name = "product")
class ProductEntity {

    @Id
    private String id;
    private String sku;
    private String name;
    private String description;
    private String category;
    @Column(name = "price_amount")
    private long priceAmount;
    private String currency;
    private int stock;
    private boolean active;
    @Column(name = "created_at")
    private OffsetDateTime createdAt;
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    @Version
    private long version;

    protected ProductEntity() {
    }

    String id() { return id; }
    String sku() { return sku; }
    String name() { return name; }
    String description() { return description; }
    String category() { return category; }
    long priceAmount() { return priceAmount; }
    String currency() { return currency; }
    int stock() { return stock; }
    boolean active() { return active; }
    OffsetDateTime createdAt() { return createdAt; }
    long version() { return version; }
}
