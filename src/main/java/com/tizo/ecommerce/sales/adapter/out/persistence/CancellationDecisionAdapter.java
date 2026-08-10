package com.tizo.ecommerce.sales.adapter.out.persistence;

import com.tizo.ecommerce.sales.application.CancellationDecisionPort;
import com.tizo.ecommerce.shared.error.DomainException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CancellationDecisionAdapter implements CancellationDecisionPort {

    private final JdbcClient jdbc;

    public CancellationDecisionAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public DecisionReference approve(
            String requestId,
            String operatorId,
            long expectedRequestVersion,
            long expectedOrderVersion,
            String note) {
        RequestLock request = lockRequest(requestId);
        requirePendingVersion(request, expectedRequestVersion);
        OrderLock order = lockOrder(request.orderId());
        if (order.version() != expectedOrderVersion) {
            throw DomainException.conflict(
                    "STALE_ORDER_VERSION", "El pedido cambió; actualice la vista antes de decidir.");
        }
        if (order.dispatchedAt() != null || "DISPATCHED".equals(order.status())
                || "DELIVERED".equals(order.status())) {
            throw DomainException.conflict(
                    "ORDER_ALREADY_DISPATCHED", "El pedido fue despachado antes de la decisión.");
        }

        List<ItemLock> items = jdbc.sql("""
                        SELECT oi.id, oi.product_id, cri.quantity, cri.amount, oi.status
                        FROM cancellation_request_item cri
                        JOIN order_item oi ON oi.id=cri.order_item_id
                        WHERE cri.request_id=:requestId AND cri.active
                        ORDER BY oi.id
                        FOR UPDATE OF oi, cri
                        """)
                .param("requestId", requestId)
                .query((row, number) -> new ItemLock(
                        row.getString("id"), row.getString("product_id"), row.getInt("quantity"),
                        row.getLong("amount"), row.getString("status")))
                .list();
        if (items.isEmpty() || items.stream().anyMatch(item -> !cancellable(item.status()))) {
            throw DomainException.conflict(
                    "ITEM_NOT_CANCELLABLE", "Una línea cambió y ya no puede cancelarse.");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        long affected = 0;
        for (ItemLock item : items) {
            affected = Math.addExact(affected, item.amount());
            jdbc.sql("""
                            UPDATE order_item
                            SET status='CANCELLED', active_amount=0, version=version+1
                            WHERE id=:itemId
                            """)
                    .param("itemId", item.id())
                    .update();
            jdbc.sql("""
                            UPDATE product
                            SET stock=stock+:quantity, version=version+1, updated_at=:now
                            WHERE id=:productId
                            """)
                    .param("quantity", item.quantity())
                    .param("now", now)
                    .param("productId", item.productId())
                    .update();
        }

        boolean fullyCancelled = jdbc.sql("""
                        SELECT NOT EXISTS (
                            SELECT 1 FROM order_item WHERE order_id=:orderId AND status<>'CANCELLED'
                        )
                        """)
                .param("orderId", request.orderId())
                .query(Boolean.class)
                .single();
        int changed = jdbc.sql("""
                        UPDATE customer_order
                        SET active_total=active_total-:affected,
                            cancellation_status=:cancellationStatus,
                            updated_at=:now,
                            version=version+1
                        WHERE id=:orderId AND version=:expectedVersion
                        """)
                .param("affected", affected)
                .param("cancellationStatus", fullyCancelled ? "FULL" : "PARTIAL")
                .param("now", now)
                .param("orderId", request.orderId())
                .param("expectedVersion", expectedOrderVersion)
                .update();
        if (changed != 1) {
            throw DomainException.conflict("STALE_ORDER_VERSION", "El pedido cambió durante la decisión.");
        }

        jdbc.sql("""
                        UPDATE cancellation_request
                        SET status='COMPLETED', assigned_operator_id=:operatorId,
                            resolved_by_operator_id=:operatorId, operator_note=:note,
                            resolved_at=:now, updated_at=:now, version=version+1
                        WHERE id=:requestId AND version=:expectedVersion AND status='PENDING'
                        """)
                .param("operatorId", operatorId)
                .param("note", normalize(note))
                .param("now", now)
                .param("requestId", requestId)
                .param("expectedVersion", expectedRequestVersion)
                .update();
        jdbc.sql("UPDATE cancellation_request_item SET active=FALSE WHERE request_id=:requestId")
                .param("requestId", requestId)
                .update();

        jdbc.sql("""
                        INSERT INTO refund
                            (id, order_id, cancellation_request_id, amount, currency, status,
                             created_at, updated_at)
                        VALUES (:id, :orderId, :requestId, :amount, 'ARS', 'PENDING', :now, :now)
                        ON CONFLICT (cancellation_request_id) DO NOTHING
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("orderId", request.orderId())
                .param("requestId", requestId)
                .param("amount", affected)
                .param("now", now)
                .update();
        insertEffect(request.orderId(), requestId, "INVENTORY", "COMPLETED", now);
        insertEffect(request.orderId(), requestId, "REFUND", "PENDING", now);
        insertEffect(request.orderId(), requestId, "CUSTOMER_NOTIFICATION", "PENDING", now);
        return new DecisionReference(requestId, request.orderId());
    }

    @Override
    public DecisionReference reject(
            String requestId,
            String operatorId,
            long expectedRequestVersion,
            String rejectionCode,
            String rejectionNote) {
        RequestLock request = lockRequest(requestId);
        requirePendingVersion(request, expectedRequestVersion);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int changed = jdbc.sql("""
                        UPDATE cancellation_request
                        SET status='REJECTED', assigned_operator_id=:operatorId,
                            resolved_by_operator_id=:operatorId, rejection_code=:rejectionCode,
                            operator_note=:note, resolved_at=:now, updated_at=:now, version=version+1
                        WHERE id=:requestId AND version=:expectedVersion AND status='PENDING'
                        """)
                .param("operatorId", operatorId)
                .param("rejectionCode", rejectionCode)
                .param("note", normalize(rejectionNote))
                .param("now", now)
                .param("requestId", requestId)
                .param("expectedVersion", expectedRequestVersion)
                .update();
        if (changed != 1) {
            throw DomainException.conflict("STALE_REQUEST_VERSION", "La solicitud cambió durante la decisión.");
        }
        jdbc.sql("UPDATE cancellation_request_item SET active=FALSE WHERE request_id=:requestId")
                .param("requestId", requestId)
                .update();
        return new DecisionReference(requestId, request.orderId());
    }

    private RequestLock lockRequest(String requestId) {
        return jdbc.sql("""
                        SELECT id, order_id, status, version
                        FROM cancellation_request
                        WHERE id=:requestId
                        FOR UPDATE
                        """)
                .param("requestId", requestId)
                .query((row, number) -> new RequestLock(
                        row.getString("id"), row.getString("order_id"), row.getString("status"),
                        row.getLong("version")))
                .optional()
                .orElseThrow(() -> DomainException.notFound(
                        "CANCELLATION_REQUEST_NOT_FOUND", "La solicitud de cancelación no existe."));
    }

    private OrderLock lockOrder(String orderId) {
        return jdbc.sql("""
                        SELECT id, status, dispatched_at, version
                        FROM customer_order WHERE id=:orderId FOR UPDATE
                        """)
                .param("orderId", orderId)
                .query((row, number) -> new OrderLock(
                        row.getString("id"), row.getString("status"),
                        row.getObject("dispatched_at", OffsetDateTime.class), row.getLong("version")))
                .single();
    }

    private void requirePendingVersion(RequestLock request, long expectedVersion) {
        if (!"PENDING".equals(request.status())) {
            throw DomainException.conflict(
                    "REQUEST_ALREADY_RESOLVED", "La solicitud ya tiene una decisión terminal.");
        }
        if (request.version() != expectedVersion) {
            throw DomainException.conflict(
                    "STALE_REQUEST_VERSION", "La solicitud cambió; actualice la vista antes de decidir.");
        }
    }

    private void insertEffect(
            String orderId, String requestId, String type, String status, OffsetDateTime now) {
        jdbc.sql("""
                        INSERT INTO operational_effect
                            (id, order_id, cancellation_request_id, effect_type, status, payload,
                             attempts, next_attempt_at, created_at, updated_at)
                        VALUES (:id, :orderId, :requestId, :type, :status, '{}'::jsonb,
                                0, :now, :now, :now)
                        ON CONFLICT (cancellation_request_id, effect_type) DO NOTHING
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("orderId", orderId)
                .param("requestId", requestId)
                .param("type", type)
                .param("status", status)
                .param("now", now)
                .update();
    }

    private boolean cancellable(String status) {
        return "CONFIRMED".equals(status) || "PENDING".equals(status) || "PREPARING".equals(status);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private record RequestLock(String id, String orderId, String status, long version) {
    }

    private record OrderLock(String id, String status, OffsetDateTime dispatchedAt, long version) {
    }

    private record ItemLock(String id, String productId, int quantity, long amount, String status) {
    }
}
