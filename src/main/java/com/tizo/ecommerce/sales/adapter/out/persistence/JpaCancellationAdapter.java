package com.tizo.ecommerce.sales.adapter.out.persistence;

import com.tizo.ecommerce.sales.application.CancellationPort;
import com.tizo.ecommerce.sales.application.CreateCancellationCommand;
import com.tizo.ecommerce.sales.domain.cancellation.CancellationRequest;
import com.tizo.ecommerce.sales.domain.order.Order;
import com.tizo.ecommerce.shared.error.DomainException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JpaCancellationAdapter implements CancellationPort {

    private final JdbcClient jdbc;
    private final JpaOrderAdapter orders;

    public JpaCancellationAdapter(JdbcClient jdbc, JpaOrderAdapter orders) {
        this.jdbc = jdbc;
        this.orders = orders;
    }

    @Override
    public CancellationRequest createPending(CreateCancellationCommand command) {
        OrderRef orderRef = jdbc.sql("""
                        SELECT id, customer_id
                        FROM customer_order
                        WHERE id = :orderId
                          AND (:operatorRequest OR customer_id = :customerId)
                        FOR UPDATE
                        """)
                .param("orderId", command.orderId())
                .param("operatorRequest", "OPERATOR".equals(command.requestedByType()))
                .param("customerId", command.customerId())
                .query((row, number) -> new OrderRef(row.getString("id"), row.getString("customer_id")))
                .optional()
                .orElseThrow(() -> DomainException.notFound(
                        "ORDER_NOT_FOUND", "El pedido no existe o no pertenece al cliente."));

        String orderId = orderRef.id();
        Order order = orders.findCustomerOrder(orderRef.customerId(), orderId).orElseThrow();
        boolean hasActiveRequest = jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM cancellation_request_item cri
                            JOIN cancellation_request cr ON cr.id = cri.request_id
                            WHERE cr.order_id = :orderId
                              AND cri.order_item_id IN (:itemIds)
                              AND cri.active
                              AND cr.status = 'PENDING'
                        )
                        """)
                .param("orderId", orderId)
                .param("itemIds", command.itemIds())
                .query(Boolean.class)
                .single();
        if (hasActiveRequest) {
            throw DomainException.conflict(
                    "ACTIVE_CANCELLATION_EXISTS",
                    "Ya existe una solicitud activa para una de las líneas seleccionadas.");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        CancellationRequest request = CancellationRequest.pending(
                UUID.randomUUID().toString(),
                order,
                command.itemIds(),
                command.reasonCode(),
                normalizedNote(command.reasonNote()),
                command.requestedByType(),
                command.requestedById(),
                command.expectedOrderVersion(),
                now);

        jdbc.sql("""
                        INSERT INTO cancellation_request
                            (id, order_id, status, reason_code, reason, requested_by_type,
                             requested_by_id, expected_order_version, requested_at, updated_at, version)
                        VALUES (:id, :orderId, 'PENDING', :reasonCode, :reason, :actorType,
                                :actorId, :expectedOrderVersion, :now, :now, 0)
                        """)
                .param("id", request.id())
                .param("orderId", request.orderId())
                .param("reasonCode", request.reasonCode())
                .param("reason", request.reasonNote())
                .param("actorType", request.requestedByType())
                .param("actorId", request.requestedById())
                .param("expectedOrderVersion", command.expectedOrderVersion())
                .param("now", request.requestedAt())
                .update();

        request.items().forEach(item -> jdbc.sql("""
                        INSERT INTO cancellation_request_item
                            (request_id, order_item_id, quantity, amount, active)
                        VALUES (:requestId, :orderItemId, :quantity, :amount, TRUE)
                        """)
                .param("requestId", request.id())
                .param("orderItemId", item.orderItemId())
                .param("quantity", item.quantity())
                .param("amount", item.amount().amount())
                .update());
        return request;
    }

    private String normalizedNote(String note) {
        return note == null || note.isBlank() ? null : note.strip();
    }

    private record OrderRef(String id, String customerId) {
    }
}
