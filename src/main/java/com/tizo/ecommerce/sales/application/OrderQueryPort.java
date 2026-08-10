package com.tizo.ecommerce.sales.application;

import com.tizo.ecommerce.sales.domain.order.Order;
import java.util.List;
import java.util.Optional;

public interface OrderQueryPort {

    Optional<Order> findCustomerOrder(String customerId, String orderId);

    OrderPage findCustomerOrders(
            String customerId,
            String status,
            int page,
            int pageSize,
            String sortBy,
            boolean ascending);

    record OrderPage(List<Order> items, int page, int pageSize, long totalItems, int totalPages) {
        public OrderPage {
            items = List.copyOf(items);
        }
    }
}
