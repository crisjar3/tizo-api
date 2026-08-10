package com.tizo.ecommerce.sales.adapter.out.persistence;

import com.tizo.ecommerce.sales.application.CheckoutPort;
import com.tizo.ecommerce.sales.application.OrderQueryPort;
import com.tizo.ecommerce.sales.domain.order.Order;
import com.tizo.ecommerce.shared.error.DomainException;
import com.tizo.ecommerce.shared.money.Money;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JpaOrderAdapter implements CheckoutPort {

    private final JdbcClient jdbc;

    public JpaOrderAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Order createOrderFromCart(String customerId) {
        CartHeader cart = jdbc.sql("SELECT id FROM cart WHERE customer_id = :customerId FOR UPDATE")
                .param("customerId", customerId)
                .query((row, number) -> new CartHeader(row.getString("id")))
                .optional()
                .orElseThrow(() -> DomainException.validation("CART_EMPTY", "El carrito está vacío."));

        List<CheckoutLine> lines = jdbc.sql("""
                        SELECT ci.product_id, ci.quantity, p.sku, p.name, p.price_amount, p.currency,
                               p.stock, p.active,
                               (SELECT pi.url FROM product_image pi WHERE pi.product_id = p.id
                                ORDER BY pi.display_order LIMIT 1) AS image_url
                        FROM cart_item ci
                        JOIN product p ON p.id = ci.product_id
                        WHERE ci.cart_id = :cartId
                        ORDER BY ci.product_id
                        FOR UPDATE OF ci, p
                        """)
                .param("cartId", cart.id())
                .query((row, number) -> new CheckoutLine(
                        row.getString("product_id"), row.getInt("quantity"), row.getString("sku"),
                        row.getString("name"), row.getLong("price_amount"), row.getString("currency"),
                        row.getInt("stock"), row.getBoolean("active"), row.getString("image_url")))
                .list();
        if (lines.isEmpty()) {
            throw DomainException.validation("CART_EMPTY", "El carrito está vacío.");
        }
        lines.forEach(line -> {
            if (!line.active() || line.quantity() > line.stock()) {
                throw DomainException.validation("INSUFFICIENT_STOCK",
                        "El stock cambió antes de confirmar el pedido.");
            }
        });

        AddressRow address = jdbc.sql("""
                        SELECT recipient_name, line1, line2, city, state, postal_code, country_code, phone
                        FROM customer_address
                        WHERE customer_id = :customerId AND is_default
                        """)
                .param("customerId", customerId)
                .query((row, number) -> new AddressRow(
                        row.getString("recipient_name"), row.getString("line1"), row.getString("line2"),
                        row.getString("city"), row.getString("state"), row.getString("postal_code"),
                        row.getString("country_code"), row.getString("phone")))
                .optional()
                .orElseThrow(() -> DomainException.validation("DELIVERY_ADDRESS_REQUIRED",
                        "El cliente no tiene una dirección de entrega predeterminada."));

        long total = lines.stream()
                .mapToLong(line -> Math.multiplyExact(line.priceAmount(), line.quantity()))
                .reduce(0L, Math::addExact);
        String orderId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.sql("""
                        INSERT INTO customer_order
                            (id, customer_id, status, cancellation_status, paid_total, active_total,
                             currency, payment_method, payment_reference, recipient_name, address_line1,
                             address_line2, city, state, postal_code, country_code, phone, store_id,
                             hub_id, dispatched_at, created_at, updated_at, version)
                        VALUES (:id, :customerId, 'AWAITING_STORES', 'NONE', :total, :total,
                                'ARS', 'DEMO', NULL, :recipientName, :line1, :line2, :city, :state,
                                :postalCode, :countryCode, :phone, 'store-001', 'hub-001', NULL,
                                :now, :now, 0)
                        """)
                .param("id", orderId)
                .param("customerId", customerId)
                .param("total", total)
                .param("recipientName", address.recipientName())
                .param("line1", address.line1())
                .param("line2", address.line2())
                .param("city", address.city())
                .param("state", address.region())
                .param("postalCode", address.postalCode())
                .param("countryCode", address.countryCode())
                .param("phone", address.phone())
                .param("now", now)
                .update();

        for (CheckoutLine line : lines) {
            int changed = jdbc.sql("""
                            UPDATE product
                            SET stock = stock - :quantity, version = version + 1, updated_at = :now
                            WHERE id = :productId AND active AND stock >= :quantity
                            """)
                    .param("quantity", line.quantity())
                    .param("now", now)
                    .param("productId", line.productId())
                    .update();
            if (changed != 1) {
                throw DomainException.validation("INSUFFICIENT_STOCK",
                        "El stock cambió antes de confirmar el pedido.");
            }
            long lineTotal = Math.multiplyExact(line.priceAmount(), line.quantity());
            jdbc.sql("""
                            INSERT INTO order_item
                                (id, order_id, product_id, product_name, sku, quantity, unit_price,
                                 active_amount, currency, status, store_id, hub_id, version)
                            VALUES (:id, :orderId, :productId, :productName, :sku, :quantity,
                                    :unitPrice, :activeAmount, :currency, 'CONFIRMED',
                                    'store-001', 'hub-001', 0)
                            """)
                    .param("id", UUID.randomUUID().toString())
                    .param("orderId", orderId)
                    .param("productId", line.productId())
                    .param("productName", line.name())
                    .param("sku", line.sku())
                    .param("quantity", line.quantity())
                    .param("unitPrice", line.priceAmount())
                    .param("activeAmount", lineTotal)
                    .param("currency", line.currency())
                    .update();
        }

        jdbc.sql("DELETE FROM cart_item WHERE cart_id = :cartId").param("cartId", cart.id()).update();
        jdbc.sql("UPDATE cart SET updated_at = :now, version = version + 1 WHERE id = :cartId")
                .param("now", now)
                .param("cartId", cart.id())
                .update();
        return findCustomerOrder(customerId, orderId).orElseThrow();
    }

    public Optional<Order> findCustomerOrder(String customerId, String orderId) {
        List<Order> result = loadOrders("o.customer_id = :customerId AND o.id = :orderId",
                Map.of("customerId", customerId, "orderId", orderId), "o.created_at DESC", 1, 0);
        return result.stream().findFirst();
    }

    public OrderQueryPort.OrderPage findCustomerOrders(
            String customerId,
            String status,
            int page,
            int pageSize,
            String sortBy,
            boolean ascending) {
        String statusClause = status == null ? "" : " AND o.status = :status";
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("customerId", customerId);
        if (status != null) {
            parameters.put("status", status);
        }
        long total = parameterize(jdbc.sql("SELECT count(*) FROM customer_order o WHERE o.customer_id = :customerId" + statusClause), parameters)
                .query(Long.class).single();
        List<Order> orders = loadOrders(
                "o.customer_id = :customerId" + statusClause,
                parameters,
                "o." + sortBy + (ascending ? " ASC" : " DESC") + ", o.id ASC",
                pageSize,
                (page - 1) * pageSize);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / pageSize);
        return new OrderQueryPort.OrderPage(orders, page, pageSize, total, totalPages);
    }

    private List<Order> loadOrders(
            String where,
            Map<String, Object> parameters,
            String orderBy,
            int limit,
            int offset) {
        String sql = """
                SELECT o.id, o.customer_id, o.status, o.cancellation_status, o.paid_total,
                       o.active_total, o.currency, o.payment_method, o.recipient_name,
                       o.address_line1, o.address_line2, o.city, o.state, o.postal_code,
                       o.country_code, o.phone, o.dispatched_at, o.created_at, o.updated_at, o.version
                FROM customer_order o
                WHERE %s
                ORDER BY %s
                LIMIT :limit OFFSET :offset
                """.formatted(where, orderBy);
        Map<String, Object> all = new LinkedHashMap<>(parameters);
        all.put("limit", limit);
        all.put("offset", offset);
        List<OrderHeader> headers = parameterize(jdbc.sql(sql), all)
                .query((row, number) -> new OrderHeader(
                        row.getString("id"), row.getString("customer_id"), row.getString("status"),
                        row.getString("cancellation_status"), row.getLong("paid_total"),
                        row.getLong("active_total"), row.getString("currency"), row.getString("payment_method"),
                        new Order.Address(row.getString("recipient_name"), row.getString("address_line1"),
                                row.getString("address_line2"), row.getString("city"), row.getString("state"),
                                row.getString("postal_code"), row.getString("country_code"), row.getString("phone")),
                        row.getObject("dispatched_at", OffsetDateTime.class),
                        row.getObject("created_at", OffsetDateTime.class),
                        row.getObject("updated_at", OffsetDateTime.class), row.getLong("version")))
                .list();
        List<String> ids = headers.stream().map(OrderHeader::id).toList();
        Map<String, List<Order.Item>> items = loadItems(ids);
        Map<String, Order.Cancellation> cancellations = loadCancellations(ids);
        return headers.stream().map(header -> new Order(
                header.id(), header.customerId(), header.status(), header.cancellationStatus(),
                new Money(header.paidTotal(), header.currency()), new Money(header.activeTotal(), header.currency()),
                header.paymentMethod(), header.address(), header.dispatchedAt(), header.createdAt(),
                header.updatedAt(), header.version(), items.getOrDefault(header.id(), List.of()),
                cancellations.get(header.id()))).toList();
    }

    private Map<String, List<Order.Item>> loadItems(List<String> orderIds) {
        Map<String, List<Order.Item>> result = new LinkedHashMap<>();
        orderIds.forEach(id -> result.put(id, new ArrayList<>()));
        if (orderIds.isEmpty()) {
            return result;
        }
        jdbc.sql("""
                        SELECT oi.order_id, oi.id, oi.product_id, oi.product_name, oi.sku,
                               oi.quantity, oi.unit_price, oi.active_amount, oi.currency, oi.status,
                               (SELECT pi.url FROM product_image pi WHERE pi.product_id = oi.product_id
                                ORDER BY pi.display_order LIMIT 1) AS image_url
                        FROM order_item oi
                        WHERE oi.order_id IN (:orderIds)
                        ORDER BY oi.order_id, oi.id
                        """)
                .param("orderIds", orderIds)
                .query((row, number) -> new OrderItemRow(
                        row.getString("order_id"),
                        new Order.Item(row.getString("id"), row.getString("product_id"),
                                row.getString("product_name"), row.getString("sku"), row.getString("image_url"),
                                row.getInt("quantity"), new Money(row.getLong("unit_price"), row.getString("currency")),
                                new Money(row.getLong("active_amount"), row.getString("currency")),
                                row.getString("status"))))
                .list()
                .forEach(row -> result.get(row.orderId()).add(row.item()));
        return result;
    }

    private Map<String, Order.Cancellation> loadCancellations(List<String> orderIds) {
        Map<String, Order.Cancellation> result = new LinkedHashMap<>();
        if (orderIds.isEmpty()) {
            return result;
        }
        jdbc.sql("""
                        SELECT cr.order_id, cr.id, cr.status, amounts.affected_amount,
                               cr.requested_at, cr.resolved_at,
                               COALESCE(r.status, 'NOT_REQUIRED') AS refund_status,
                               r.amount AS refund_amount, r.updated_at AS refund_updated_at
                        FROM cancellation_request cr
                        JOIN (
                            SELECT request_id, SUM(amount) AS affected_amount
                            FROM cancellation_request_item
                            GROUP BY request_id
                        ) amounts ON amounts.request_id = cr.id
                        LEFT JOIN refund r ON r.cancellation_request_id = cr.id
                        WHERE cr.order_id IN (:orderIds)
                        ORDER BY cr.order_id, cr.requested_at DESC
                        """)
                .param("orderIds", orderIds)
                .query((row, number) -> new CancellationRow(
                        row.getString("order_id"),
                        new Order.Cancellation(
                                row.getString("id"),
                                row.getString("status"),
                                Money.ars(row.getLong("affected_amount")),
                                row.getObject("requested_at", OffsetDateTime.class),
                                row.getObject("resolved_at", OffsetDateTime.class),
                                row.getString("refund_status"),
                                row.getObject("refund_amount") == null
                                        ? null
                                        : Money.ars(row.getLong("refund_amount")),
                                row.getObject("refund_updated_at", OffsetDateTime.class))))
                .list()
                .forEach(row -> result.putIfAbsent(row.orderId(), row.cancellation()));
        return result;
    }

    private JdbcClient.StatementSpec parameterize(JdbcClient.StatementSpec statement, Map<String, Object> parameters) {
        JdbcClient.StatementSpec current = statement;
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            current = current.param(entry.getKey(), entry.getValue());
        }
        return current;
    }

    private record CartHeader(String id) {
    }

    private record CheckoutLine(
            String productId, int quantity, String sku, String name, long priceAmount,
            String currency, int stock, boolean active, String imageUrl) {
    }

    private record AddressRow(
            String recipientName, String line1, String line2, String city,
            String region, String postalCode, String countryCode, String phone) {
    }

    private record OrderHeader(
            String id, String customerId, String status, String cancellationStatus,
            long paidTotal, long activeTotal, String currency, String paymentMethod,
            Order.Address address, OffsetDateTime dispatchedAt, OffsetDateTime createdAt,
            OffsetDateTime updatedAt, long version) {
    }

    private record OrderItemRow(String orderId, Order.Item item) {
    }

    private record CancellationRow(String orderId, Order.Cancellation cancellation) {
    }
}
