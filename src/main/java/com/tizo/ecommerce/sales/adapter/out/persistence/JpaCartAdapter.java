package com.tizo.ecommerce.sales.adapter.out.persistence;

import com.tizo.ecommerce.sales.application.CartPort;
import com.tizo.ecommerce.sales.domain.cart.Cart;
import com.tizo.ecommerce.shared.money.Money;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JpaCartAdapter implements CartPort {

    private final JdbcClient jdbc;

    public JpaCartAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Cart getOrCreate(String customerId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.sql("""
                        INSERT INTO cart (id, customer_id, created_at, updated_at, version)
                        VALUES (:id, :customerId, :now, :now, 0)
                        ON CONFLICT (customer_id) DO NOTHING
                        """)
                .param("id", "cart-" + customerId)
                .param("customerId", customerId)
                .param("now", now)
                .update();

        CartHeader header = jdbc.sql("SELECT id, customer_id, updated_at FROM cart WHERE customer_id = :customerId")
                .param("customerId", customerId)
                .query((row, number) -> new CartHeader(
                        row.getString("id"),
                        row.getString("customer_id"),
                        row.getObject("updated_at", OffsetDateTime.class)))
                .single();

        List<Cart.Item> items = new ArrayList<>(jdbc.sql("""
                        SELECT ci.product_id, p.name,
                               (SELECT pi.url FROM product_image pi
                                WHERE pi.product_id = p.id ORDER BY pi.display_order LIMIT 1) AS image_url,
                               p.price_amount, p.currency, ci.quantity, p.stock
                        FROM cart_item ci
                        JOIN product p ON p.id = ci.product_id
                        WHERE ci.cart_id = :cartId
                        ORDER BY ci.added_at, ci.product_id
                        """)
                .param("cartId", header.id())
                .query((row, number) -> new Cart.Item(
                        row.getString("product_id"),
                        row.getString("name"),
                        row.getString("image_url"),
                        new Money(row.getLong("price_amount"), row.getString("currency")),
                        row.getInt("quantity"),
                        row.getInt("stock")))
                .list());
        return new Cart(header.id(), header.customerId(), items, header.updatedAt());
    }

    @Override
    public void putItem(String customerId, String productId, int quantity) {
        String cartId = cartId(customerId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.sql("""
                        INSERT INTO cart_item (cart_id, product_id, quantity, added_at, updated_at)
                        VALUES (:cartId, :productId, :quantity, :now, :now)
                        ON CONFLICT (cart_id, product_id)
                        DO UPDATE SET quantity = EXCLUDED.quantity, updated_at = EXCLUDED.updated_at
                        """)
                .param("cartId", cartId)
                .param("productId", productId)
                .param("quantity", quantity)
                .param("now", now)
                .update();
        touch(cartId, now);
    }

    @Override
    public void deleteItem(String customerId, String productId) {
        String cartId = cartId(customerId);
        jdbc.sql("DELETE FROM cart_item WHERE cart_id = :cartId AND product_id = :productId")
                .param("cartId", cartId)
                .param("productId", productId)
                .update();
        touch(cartId, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private String cartId(String customerId) {
        return jdbc.sql("SELECT id FROM cart WHERE customer_id = :customerId")
                .param("customerId", customerId)
                .query(String.class)
                .single();
    }

    private void touch(String cartId, OffsetDateTime now) {
        jdbc.sql("UPDATE cart SET updated_at = :now, version = version + 1 WHERE id = :cartId")
                .param("now", now)
                .param("cartId", cartId)
                .update();
    }

    private record CartHeader(String id, String customerId, OffsetDateTime updatedAt) {
    }
}
