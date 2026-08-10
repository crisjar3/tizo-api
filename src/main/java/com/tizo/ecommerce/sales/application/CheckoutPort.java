package com.tizo.ecommerce.sales.application;

import com.tizo.ecommerce.sales.domain.order.Order;

public interface CheckoutPort {

    Order createOrderFromCart(String customerId);
}
