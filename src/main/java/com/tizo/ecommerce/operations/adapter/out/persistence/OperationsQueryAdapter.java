package com.tizo.ecommerce.operations.adapter.out.persistence;

import com.tizo.ecommerce.operations.application.OperationsProjection;
import com.tizo.ecommerce.operations.application.OperationsQueryPort;
import com.tizo.ecommerce.operations.domain.Operator;
import com.tizo.ecommerce.sales.domain.order.Order;
import com.tizo.ecommerce.shared.money.Money;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OperationsQueryAdapter implements OperationsQueryPort {

    private static final OffsetDateTime DATE_SENTINEL = OffsetDateTime.of(
            2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private final JdbcClient jdbc;
    private final JpaOperatorAdapter operators;

    public OperationsQueryAdapter(JdbcClient jdbc, JpaOperatorAdapter operators) {
        this.jdbc = jdbc;
        this.operators = operators;
    }

    @Override
    public List<Operator> findOperators(Boolean active, String search) {
        return operators.find(active, search);
    }

    @Override
    public OperationsProjection.Page<OperationsProjection.OrderView> findOrders(
            String search,
            String status,
            String cancellationStatus,
            String storeId,
            String hubId,
            OffsetDateTime createdFrom,
            OffsetDateTime createdTo,
            int page,
            int pageSize,
            String sortColumn,
            boolean ascending) {
        OrderFilter filter = new OrderFilter(search, status, cancellationStatus, storeId, hubId, createdFrom, createdTo);
        long total = bindOrderFilter(
                        jdbc.sql("SELECT count(*) FROM (SELECT o.id " + orderFromWhere() + ") counted"),
                        filter)
                .query(Long.class).single();
        String sql = orderSelect() + orderFromWhere()
                + " ORDER BY " + sortColumn + (ascending ? " ASC" : " DESC")
                + ", o.id ASC LIMIT :limit OFFSET :offset";
        JdbcClient.StatementSpec statement = bindOrderFilter(jdbc.sql(sql), filter)
                .param("limit", pageSize)
                .param("offset", (page - 1) * pageSize);
        List<OperationsProjection.OrderView> items = statement
                .query((row, number) -> toOrderView(mapOrderHeader(row), List.of()))
                .list();
        return new OperationsProjection.Page<>(items, page, pageSize, total, pages(total, pageSize));
    }

    @Override
    public Optional<OperationsProjection.OrderView> findOrder(String orderId) {
        Optional<OrderHeader> header = jdbc.sql(orderSelect() + orderFromWhere("AND o.id = :orderId"))
                .param("search", "")
                .param("hasStatus", false)
                .param("status", "")
                .param("hasCancellationStatus", false)
                .param("cancellationStatus", "")
                .param("storeId", "")
                .param("hubId", "")
                .param("hasCreatedFrom", false)
                .param("createdFrom", DATE_SENTINEL)
                .param("hasCreatedTo", false)
                .param("createdTo", DATE_SENTINEL)
                .param("orderId", orderId)
                .query((row, number) -> mapOrderHeader(row))
                .optional();
        return header.map(value -> toOrderView(value, loadOrderItems(orderId)));
    }

    @Override
    public OperationsProjection.CancellationPage findCancellations(
            String status,
            String search,
            String reasonCode,
            String requestedByType,
            String operatorId,
            OffsetDateTime createdFrom,
            OffsetDateTime createdTo,
            int page,
            int pageSize,
            String sortColumn,
            boolean ascending,
            boolean terminalOnly,
            String rejectionCode,
            OffsetDateTime resolvedFrom,
            OffsetDateTime resolvedTo) {
        CancellationFilter filter = new CancellationFilter(
                status, search, reasonCode, requestedByType, operatorId, createdFrom, createdTo,
                terminalOnly, rejectionCode, resolvedFrom, resolvedTo);
        long total = bindCancellationFilter(jdbc.sql("SELECT count(*) " + cancellationFromWhere()), filter)
                .query(Long.class).single();
        String sql = cancellationSelect() + cancellationFromWhere()
                + " ORDER BY " + sortColumn + (ascending ? " ASC" : " DESC")
                + " NULLS LAST, cr.id ASC LIMIT :limit OFFSET :offset";
        List<OperationsProjection.CancellationView> items = bindCancellationFilter(jdbc.sql(sql), filter)
                .param("limit", pageSize)
                .param("offset", (page - 1) * pageSize)
                .query((row, number) -> toCancellationView(mapCancellationHeader(row), false))
                .list();
        OperationsProjection.CancellationCounts counts = cancellationCounts();
        return new OperationsProjection.CancellationPage(
                new OperationsProjection.Page<>(items, page, pageSize, total, pages(total, pageSize)), counts);
    }

    @Override
    public Optional<OperationsProjection.CancellationView> findCancellation(String requestId) {
        return jdbc.sql(cancellationSelect() + """
                        FROM cancellation_request cr
                        JOIN customer_order o ON o.id = cr.order_id
                        JOIN customer c ON c.id = o.customer_id
                        LEFT JOIN operator_account requested_op
                            ON cr.requested_by_type = 'OPERATOR' AND requested_op.id = cr.requested_by_id
                        LEFT JOIN operator_account resolved_op ON resolved_op.id = cr.resolved_by_operator_id
                        WHERE cr.id = :requestId
                        """)
                .param("requestId", requestId)
                .query((row, number) -> mapCancellationHeader(row))
                .optional()
                .map(header -> toCancellationView(header, true));
    }

    private String orderSelect() {
        return """
                SELECT o.id, o.customer_id, CONCAT(c.first_name, ' ', c.last_name) customer_name,
                       c.email customer_email, o.status, o.cancellation_status, o.paid_total,
                       o.active_total, o.currency, o.payment_method, o.recipient_name,
                       o.address_line1, o.address_line2, o.city, o.state, o.postal_code,
                       o.country_code, o.phone, o.store_id, s.name store_name, o.hub_id,
                       h.name hub_name, o.dispatched_at, o.created_at, o.updated_at, o.version,
                       COALESCE(SUM(oi.quantity), 0) total_items,
                       COALESCE(SUM(oi.quantity) FILTER (WHERE oi.status = 'CANCELLED'), 0) cancelled_items,
                       (SELECT cr.id FROM cancellation_request cr
                        WHERE cr.order_id=o.id AND cr.status='PENDING'
                        ORDER BY cr.requested_at DESC LIMIT 1) active_request_id
                """;
    }

    private String orderFromWhere() {
        return orderFromWhere("");
    }

    private String orderFromWhere(String additionalPredicate) {
        return """
                 FROM customer_order o
                 JOIN customer c ON c.id=o.customer_id
                 LEFT JOIN store s ON s.id=o.store_id
                 LEFT JOIN fulfillment_hub h ON h.id=o.hub_id
                 LEFT JOIN order_item oi ON oi.order_id=o.id
                 WHERE (:search='' OR LOWER(o.id) LIKE :search OR LOWER(c.email) LIKE :search
                        OR LOWER(CONCAT(c.first_name, ' ', c.last_name)) LIKE :search)
                   AND (:hasStatus=FALSE OR o.status=:status)
                   AND (:hasCancellationStatus=FALSE OR o.cancellation_status=:cancellationStatus)
                   AND (:storeId='' OR o.store_id=:storeId)
                   AND (:hubId='' OR o.hub_id=:hubId)
                   AND (:hasCreatedFrom=FALSE OR o.created_at>=:createdFrom)
                   AND (:hasCreatedTo=FALSE OR o.created_at<=:createdTo)
                 %s
                 GROUP BY o.id, c.id, s.name, h.name
                """.formatted(additionalPredicate);
    }

    private JdbcClient.StatementSpec bindOrderFilter(JdbcClient.StatementSpec statement, OrderFilter filter) {
        String search = filter.search() == null ? "" : "%" + filter.search().toLowerCase() + "%";
        return statement
                .param("search", search)
                .param("hasStatus", filter.status() != null)
                .param("status", filter.status() == null ? "" : filter.status())
                .param("hasCancellationStatus", filter.cancellationStatus() != null)
                .param("cancellationStatus", filter.cancellationStatus() == null ? "" : filter.cancellationStatus())
                .param("storeId", filter.storeId() == null ? "" : filter.storeId())
                .param("hubId", filter.hubId() == null ? "" : filter.hubId())
                .param("hasCreatedFrom", filter.createdFrom() != null)
                .param("createdFrom", filter.createdFrom() == null ? DATE_SENTINEL : filter.createdFrom())
                .param("hasCreatedTo", filter.createdTo() != null)
                .param("createdTo", filter.createdTo() == null ? DATE_SENTINEL : filter.createdTo());
    }

    private OrderHeader mapOrderHeader(java.sql.ResultSet row) throws java.sql.SQLException {
        return new OrderHeader(
                row.getString("id"), row.getString("customer_id"), row.getString("customer_name"),
                row.getString("customer_email"), row.getString("status"), row.getString("cancellation_status"),
                row.getLong("paid_total"), row.getLong("active_total"), row.getString("currency"),
                row.getString("payment_method"),
                new Order.Address(
                        row.getString("recipient_name"), row.getString("address_line1"), row.getString("address_line2"),
                        row.getString("city"), row.getString("state"), row.getString("postal_code"),
                        row.getString("country_code"), row.getString("phone")),
                row.getString("store_id"), row.getString("store_name"), row.getString("hub_id"),
                row.getString("hub_name"), row.getObject("dispatched_at", OffsetDateTime.class),
                row.getObject("created_at", OffsetDateTime.class), row.getObject("updated_at", OffsetDateTime.class),
                row.getLong("version"), row.getInt("total_items"), row.getInt("cancelled_items"),
                row.getString("active_request_id"));
    }

    private OperationsProjection.OrderView toOrderView(
            OrderHeader header, List<OperationsProjection.OrderItem> items) {
        Order order = new Order(
                header.id(), header.customerId(), header.status(), header.cancellationStatus(),
                new Money(header.paidTotal(), header.currency()), new Money(header.activeTotal(), header.currency()),
                header.paymentMethod(), header.address(), header.dispatchedAt(), header.createdAt(), header.updatedAt(),
                header.version(), items.stream().map(OperationsProjection.OrderItem::item).toList(), null);
        List<OperationsProjection.Store> stores = header.storeId() == null
                ? List.of()
                : List.of(new OperationsProjection.Store(header.storeId(), header.storeName()));
        OperationsProjection.Hub hub = header.hubId() == null
                ? null
                : new OperationsProjection.Hub(header.hubId(), header.hubName());
        return new OperationsProjection.OrderView(
                order,
                new OperationsProjection.Customer(header.customerId(), header.customerName(), header.customerEmail()),
                stores, hub, items, header.activeRequestId(), header.totalItems(), header.cancelledItems());
    }

    private List<OperationsProjection.OrderItem> loadOrderItems(String orderId) {
        return jdbc.sql("""
                        SELECT oi.id, oi.product_id, oi.product_name, oi.sku, oi.quantity,
                               oi.unit_price, oi.active_amount, oi.currency, oi.status,
                               oi.store_id, s.name store_name,
                               (SELECT pi.url FROM product_image pi WHERE pi.product_id=oi.product_id
                                ORDER BY pi.display_order LIMIT 1) image_url
                        FROM order_item oi
                        LEFT JOIN store s ON s.id=oi.store_id
                        WHERE oi.order_id=:orderId
                        ORDER BY oi.id
                        """)
                .param("orderId", orderId)
                .query((row, number) -> new OperationsProjection.OrderItem(
                        new Order.Item(
                                row.getString("id"), row.getString("product_id"), row.getString("product_name"),
                                row.getString("sku"), row.getString("image_url"), row.getInt("quantity"),
                                new Money(row.getLong("unit_price"), row.getString("currency")),
                                new Money(row.getLong("active_amount"), row.getString("currency")),
                                row.getString("status")),
                        row.getString("store_id"), row.getString("store_name")))
                .list();
    }

    private String cancellationSelect() {
        return """
                SELECT cr.id, cr.order_id, cr.status, cr.reason_code, cr.reason, cr.requested_by_type,
                       cr.requested_by_id,
                       CASE WHEN cr.requested_by_type='OPERATOR' THEN requested_op.display_name
                            ELSE CONCAT(c.first_name, ' ', c.last_name) END requested_by_name,
                       cr.resolved_by_operator_id, resolved_op.display_name resolved_by_name,
                       cr.rejection_code, cr.operator_note, cr.invalidated_by, cr.requested_at,
                       cr.resolved_at, cr.updated_at, cr.version, cr.expected_order_version,
                       o.version current_order_version, o.dispatched_at,
                       COALESCE((SELECT SUM(cri.amount) FROM cancellation_request_item cri
                                 WHERE cri.request_id=cr.id), 0) requested_amount,
                       COALESCE((SELECT COUNT(*) FROM cancellation_request_item cri
                                 WHERE cri.request_id=cr.id), 0) item_count,
                       COALESCE((SELECT SUM(oi.active_amount)
                                 FROM cancellation_request_item cri
                                 JOIN order_item oi ON oi.id=cri.order_item_id
                                 WHERE cri.request_id=cr.id), 0) current_affected_amount,
                       COALESCE((SELECT r.status FROM refund r
                                 WHERE r.cancellation_request_id=cr.id), 'NOT_REQUIRED') refund_status
                """;
    }

    private String cancellationFromWhere() {
        return """
                 FROM cancellation_request cr
                 JOIN customer_order o ON o.id=cr.order_id
                 JOIN customer c ON c.id=o.customer_id
                 LEFT JOIN operator_account requested_op
                    ON cr.requested_by_type='OPERATOR' AND requested_op.id=cr.requested_by_id
                 LEFT JOIN operator_account resolved_op ON resolved_op.id=cr.resolved_by_operator_id
                 WHERE (:hasStatus=FALSE OR cr.status=:status)
                   AND (:search='' OR LOWER(cr.id) LIKE :search OR LOWER(cr.order_id) LIKE :search)
                   AND (:reasonCode='' OR cr.reason_code=:reasonCode)
                   AND (:requestedByType='' OR cr.requested_by_type=:requestedByType)
                   AND (:operatorId='' OR cr.requested_by_id=:operatorId OR cr.resolved_by_operator_id=:operatorId)
                   AND (:hasCreatedFrom=FALSE OR cr.requested_at>=:createdFrom)
                   AND (:hasCreatedTo=FALSE OR cr.requested_at<=:createdTo)
                   AND (:terminalOnly=FALSE OR cr.status IN ('COMPLETED','REJECTED'))
                   AND (:rejectionCode='' OR cr.rejection_code=:rejectionCode)
                   AND (:hasResolvedFrom=FALSE OR cr.resolved_at>=:resolvedFrom)
                   AND (:hasResolvedTo=FALSE OR cr.resolved_at<=:resolvedTo)
                """;
    }

    private JdbcClient.StatementSpec bindCancellationFilter(
            JdbcClient.StatementSpec statement, CancellationFilter filter) {
        String search = filter.search() == null ? "" : "%" + filter.search().toLowerCase() + "%";
        return statement
                .param("hasStatus", filter.status() != null)
                .param("status", filter.status() == null ? "" : filter.status())
                .param("search", search)
                .param("reasonCode", filter.reasonCode() == null ? "" : filter.reasonCode())
                .param("requestedByType", filter.requestedByType() == null ? "" : filter.requestedByType())
                .param("operatorId", filter.operatorId() == null ? "" : filter.operatorId())
                .param("hasCreatedFrom", filter.createdFrom() != null)
                .param("createdFrom", filter.createdFrom() == null ? DATE_SENTINEL : filter.createdFrom())
                .param("hasCreatedTo", filter.createdTo() != null)
                .param("createdTo", filter.createdTo() == null ? DATE_SENTINEL : filter.createdTo())
                .param("terminalOnly", filter.terminalOnly())
                .param("rejectionCode", filter.rejectionCode() == null ? "" : filter.rejectionCode())
                .param("hasResolvedFrom", filter.resolvedFrom() != null)
                .param("resolvedFrom", filter.resolvedFrom() == null ? DATE_SENTINEL : filter.resolvedFrom())
                .param("hasResolvedTo", filter.resolvedTo() != null)
                .param("resolvedTo", filter.resolvedTo() == null ? DATE_SENTINEL : filter.resolvedTo());
    }

    private CancellationHeader mapCancellationHeader(java.sql.ResultSet row) throws java.sql.SQLException {
        return new CancellationHeader(
                row.getString("id"), row.getString("order_id"), row.getString("status"),
                row.getString("reason_code"), row.getString("reason"), row.getString("requested_by_type"),
                row.getString("requested_by_id"), row.getString("requested_by_name"),
                row.getString("resolved_by_operator_id"), row.getString("resolved_by_name"),
                row.getString("rejection_code"), row.getString("operator_note"), row.getString("invalidated_by"),
                row.getObject("requested_at", OffsetDateTime.class), row.getObject("resolved_at", OffsetDateTime.class),
                row.getLong("version"), row.getLong("expected_order_version"),
                row.getLong("current_order_version"), row.getObject("dispatched_at", OffsetDateTime.class),
                row.getLong("requested_amount"), row.getLong("current_affected_amount"),
                row.getInt("item_count"), row.getString("refund_status"));
    }

    private OperationsProjection.CancellationView toCancellationView(CancellationHeader header, boolean detailed) {
        boolean pending = "PENDING".equals(header.status());
        boolean stillValid = pending && header.dispatchedAt() == null;
        String invalidatedBy = header.invalidatedBy();
        if (!stillValid && invalidatedBy == null) {
            invalidatedBy = header.dispatchedAt() != null ? "DISPATCHED" : "REQUEST_ALREADY_RESOLVED";
        }
        return new OperationsProjection.CancellationView(
                header.id(), header.orderId(), displayNumber(header.orderId()), header.status(),
                new OperationsProjection.Actor(
                        header.requestedByType(), header.requestedById(), header.requestedByName()),
                header.resolvedById() == null ? null : new OperationsProjection.Actor(
                        "OPERATOR", header.resolvedById(), header.resolvedByName()),
                header.requestedAt(), header.resolvedAt(), header.reasonCode(), header.reasonNote(),
                header.rejectionCode(), header.operatorNote(),
                detailed ? loadCancellationItems(header.id(), stillValid) : List.of(),
                Money.ars(header.requestedAmount()), Money.ars(header.currentAffectedAmount()),
                header.expectedOrderVersion(), header.currentOrderVersion(), header.dispatchedAt(),
                stillValid, invalidatedBy, detailed ? loadRefund(header.id()) : emptyRefund(header.refundStatus()),
                detailed ? loadEffects(header.id()) : List.of(), detailed ? loadAudit(header.id()) : List.of(),
                header.itemCount(), header.version());
    }

    private List<OperationsProjection.CancellationItem> loadCancellationItems(String requestId, boolean valid) {
        return jdbc.sql("""
                        SELECT oi.id, oi.product_id, oi.product_name, oi.store_id, s.name store_name,
                               cri.quantity, oi.unit_price, cri.amount, oi.currency, oi.status
                        FROM cancellation_request_item cri
                        JOIN order_item oi ON oi.id=cri.order_item_id
                        LEFT JOIN store s ON s.id=oi.store_id
                        WHERE cri.request_id=:requestId
                        ORDER BY oi.id
                        """)
                .param("requestId", requestId)
                .query((row, number) -> new OperationsProjection.CancellationItem(
                        row.getString("id"), row.getString("product_id"), row.getString("product_name"),
                        row.getString("store_id"), row.getString("store_name"), row.getInt("quantity"),
                        new Money(row.getLong("unit_price"), row.getString("currency")),
                        new Money(row.getLong("amount"), row.getString("currency")), row.getString("status"),
                        valid && !"CANCELLED".equals(row.getString("status"))))
                .list();
    }

    private OperationsProjection.Refund loadRefund(String requestId) {
        return jdbc.sql("""
                        SELECT status, amount, currency, provider_reference, updated_at, NULL AS failure_code
                        FROM refund WHERE cancellation_request_id=:requestId
                        """)
                .param("requestId", requestId)
                .query((row, number) -> new OperationsProjection.Refund(
                        row.getString("status"),
                        row.getObject("amount") == null ? null : new Money(
                                row.getLong("amount"), row.getString("currency")),
                        row.getString("provider_reference"), row.getObject("updated_at", OffsetDateTime.class),
                        row.getString("failure_code")))
                .optional()
                .orElseGet(() -> emptyRefund("NOT_REQUIRED"));
    }

    private OperationsProjection.Refund emptyRefund(String status) {
        return new OperationsProjection.Refund(status, null, null, null, null);
    }

    private List<OperationsProjection.Effect> loadEffects(String requestId) {
        return jdbc.sql("""
                        SELECT effect_type, status, updated_at, last_error
                        FROM operational_effect
                        WHERE cancellation_request_id=:requestId
                        ORDER BY effect_type
                        """)
                .param("requestId", requestId)
                .query((row, number) -> new OperationsProjection.Effect(
                        row.getString("effect_type"), row.getString("status"),
                        row.getObject("updated_at", OffsetDateTime.class),
                        row.getString("last_error") == null ? null : "EFFECT_FAILED"))
                .list();
    }

    private List<OperationsProjection.Audit> loadAudit(String requestId) {
        return jdbc.sql("""
                        SELECT ae.id, ae.action, ae.actor_type, ae.actor_id,
                               COALESCE(op.display_name, CONCAT(c.first_name, ' ', c.last_name), ae.actor_id) actor_name,
                               ae.occurred_at, ae.correlation_id
                        FROM audit_event ae
                        LEFT JOIN operator_account op ON ae.actor_type='OPERATOR' AND op.id=ae.actor_id
                        LEFT JOIN customer c ON ae.actor_type='CUSTOMER' AND c.id=ae.actor_id
                        WHERE ae.aggregate_type='CANCELLATION_REQUEST' AND ae.aggregate_id=:requestId
                        ORDER BY ae.occurred_at, ae.id
                        """)
                .param("requestId", requestId)
                .query((row, number) -> new OperationsProjection.Audit(
                        row.getString("id"), normalizeAuditAction(row.getString("action")),
                        new OperationsProjection.Actor(
                                row.getString("actor_type"), row.getString("actor_id"), row.getString("actor_name")),
                        row.getObject("occurred_at", OffsetDateTime.class), null,
                        row.getString("correlation_id")))
                .list();
    }

    private String normalizeAuditAction(String action) {
        return switch (action) {
            case "CANCELLATION_REQUESTED" -> "REQUEST_CREATED";
            case "CANCELLATION_APPROVED" -> "REQUEST_APPROVED";
            case "CANCELLATION_REJECTED" -> "REQUEST_REJECTED";
            default -> action;
        };
    }

    private OperationsProjection.CancellationCounts cancellationCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.sql("SELECT status, count(*) total FROM cancellation_request GROUP BY status")
                .query((row, number) -> Map.entry(row.getString("status"), row.getLong("total")))
                .list()
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return new OperationsProjection.CancellationCounts(
                counts.getOrDefault("PENDING", 0L), counts.getOrDefault("COMPLETED", 0L),
                counts.getOrDefault("REJECTED", 0L));
    }

    private int pages(long total, int pageSize) {
        return total == 0 ? 0 : (int) Math.ceil((double) total / pageSize);
    }

    private String displayNumber(String id) {
        String compact = id.replace("-", "").toUpperCase();
        return "TZ-" + compact.substring(0, Math.min(10, compact.length()));
    }

    private record OrderFilter(
            String search, String status, String cancellationStatus, String storeId, String hubId,
            OffsetDateTime createdFrom, OffsetDateTime createdTo) {
    }

    private record CancellationFilter(
            String status, String search, String reasonCode, String requestedByType, String operatorId,
            OffsetDateTime createdFrom, OffsetDateTime createdTo, boolean terminalOnly,
            String rejectionCode, OffsetDateTime resolvedFrom, OffsetDateTime resolvedTo) {
    }

    private record OrderHeader(
            String id, String customerId, String customerName, String customerEmail, String status,
            String cancellationStatus, long paidTotal, long activeTotal, String currency,
            String paymentMethod, Order.Address address, String storeId, String storeName,
            String hubId, String hubName, OffsetDateTime dispatchedAt, OffsetDateTime createdAt,
            OffsetDateTime updatedAt, long version, int totalItems, int cancelledItems,
            String activeRequestId) {
    }

    private record CancellationHeader(
            String id, String orderId, String status, String reasonCode, String reasonNote,
            String requestedByType, String requestedById, String requestedByName,
            String resolvedById, String resolvedByName, String rejectionCode, String operatorNote,
            String invalidatedBy, OffsetDateTime requestedAt, OffsetDateTime resolvedAt, long version,
            long expectedOrderVersion, long currentOrderVersion, OffsetDateTime dispatchedAt,
            long requestedAmount, long currentAffectedAmount, int itemCount, String refundStatus) {
    }
}
