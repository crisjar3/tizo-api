package com.tizo.ecommerce.sales.application;

import com.tizo.ecommerce.catalog.application.CatalogService;
import com.tizo.ecommerce.catalog.domain.Product;
import com.tizo.ecommerce.sales.domain.cart.Cart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartPort carts;
    private final CatalogService catalog;

    public CartService(CartPort carts, CatalogService catalog) {
        this.carts = carts;
        this.catalog = catalog;
    }

    @Transactional
    public Cart get(String customerId) {
        return carts.getOrCreate(customerId);
    }

    @Transactional
    public Cart put(String customerId, String productId, int quantity) {
        Product product = catalog.get(productId);
        product.requireAvailableQuantity(quantity);
        carts.putItem(customerId, productId, quantity);
        return carts.getOrCreate(customerId);
    }

    @Transactional
    public void delete(String customerId, String productId) {
        carts.getOrCreate(customerId);
        carts.deleteItem(customerId, productId);
    }
}
