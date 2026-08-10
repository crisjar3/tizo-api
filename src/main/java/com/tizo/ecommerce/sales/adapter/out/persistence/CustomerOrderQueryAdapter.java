package com.tizo.ecommerce.sales.adapter.out.persistence;

import com.tizo.ecommerce.sales.application.OrderQueryPort;
import com.tizo.ecommerce.sales.domain.order.Order;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerOrderQueryAdapter implements OrderQueryPort {

    private final JpaOrderAdapter orders;

    public CustomerOrderQueryAdapter(JpaOrderAdapter orders) {
        this.orders = orders;
    }

    @Override
    public Optional<Order> findCustomerOrder(String customerId, String orderId) {
        return orders.findCustomerOrder(customerId, orderId);
    }

    @Override
    public OrderPage findCustomerOrders(
            String customerId,
            String status,
            int page,
            int pageSize,
            String sortBy,
            boolean ascending) {
        return orders.findCustomerOrders(customerId, status, page, pageSize, sortBy, ascending);
    }
}
