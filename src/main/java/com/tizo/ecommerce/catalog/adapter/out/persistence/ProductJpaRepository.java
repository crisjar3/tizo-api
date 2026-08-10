package com.tizo.ecommerce.catalog.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface ProductJpaRepository extends JpaRepository<ProductEntity, String>, JpaSpecificationExecutor<ProductEntity> {
}
