package com.tizo.ecommerce.sales.application;

import com.tizo.ecommerce.sales.domain.order.Order;
import com.tizo.ecommerce.shared.error.DomainException;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerOrderQueryService {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "createdAt", "created_at",
            "updatedAt", "updated_at",
            "displayNumber", "id");

    private final OrderQueryPort orders;

    public CustomerOrderQueryService(OrderQueryPort orders) {
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public Order get(String customerId, String orderId) {
        return orders.findCustomerOrder(customerId, orderId)
                .orElseThrow(() -> DomainException.notFound("ORDER_NOT_FOUND", "El pedido no existe."));
    }

    @Transactional(readOnly = true)
    public OrderQueryPort.OrderPage list(
            String customerId,
            String status,
            int page,
            int pageSize,
            String sortBy,
            String sortDirection) {
        String column = SORT_FIELDS.get(sortBy);
        if (column == null) {
            throw DomainException.validation("INVALID_SORT_FIELD", "El campo de ordenamiento no está permitido.");
        }
        if (!"asc".equalsIgnoreCase(sortDirection) && !"desc".equalsIgnoreCase(sortDirection)) {
            throw DomainException.validation("INVALID_SORT_DIRECTION", "La dirección debe ser asc o desc.");
        }
        return orders.findCustomerOrders(customerId, status, page, pageSize, column,
                "asc".equalsIgnoreCase(sortDirection));
    }
}
