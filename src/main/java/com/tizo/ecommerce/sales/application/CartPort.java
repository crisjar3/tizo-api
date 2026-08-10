package com.tizo.ecommerce.sales.application;

import com.tizo.ecommerce.sales.domain.cart.Cart;

public interface CartPort {

    Cart getOrCreate(String customerId);

    void putItem(String customerId, String productId, int quantity);

    void deleteItem(String customerId, String productId);
}
