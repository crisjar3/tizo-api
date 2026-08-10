package com.tizo.ecommerce.sales;

import static org.assertj.core.api.Assertions.assertThat;

import com.tizo.ecommerce.sales.adapter.in.web.CustomerOrderWebMapper;
import com.tizo.ecommerce.sales.domain.order.Order;
import com.tizo.ecommerce.shared.money.Money;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CustomerOrderProjectionTest {

    @Test
    void serializedCustomerProjectionDoesNotLeakOperationalFields() throws Exception {
        OffsetDateTime now = OffsetDateTime.of(2026, 8, 9, 18, 0, 0, 0, ZoneOffset.UTC);
        Order order = new Order(
                "order-001",
                "customer-001",
                "AWAITING_STORES",
                "NONE",
                Money.ars(500_000),
                Money.ars(500_000),
                "DEMO",
                new Order.Address(
                        "Cliente Demo", "Calle 1", null, "Buenos Aires", "CABA",
                        "1000", "AR", "+541100000000"),
                now,
                now,
                now,
                3,
                List.of(new Order.Item(
                        "item-001", "product-001", "Producto", "SKU-001",
                        "https://example.test/product.jpg", 1, Money.ars(500_000),
                        Money.ars(500_000), "ON_THE_WAY")),
                null);

        String json = JsonMapper.builder()
                .findAndAddModules()
                .build()
                .writeValueAsString(new CustomerOrderWebMapper().toDetail(order));

        assertThat(json)
                .contains("\"deliveryAddress\"")
                .contains("\"customerStatus\":\"ON_THE_WAY\"")
                .doesNotContain("customerId")
                .doesNotContain("paymentMethod")
                .doesNotContain("dispatchedAt")
                .doesNotContain("storeId")
                .doesNotContain("hubId")
                .doesNotContain("operatorNote");
    }
}
