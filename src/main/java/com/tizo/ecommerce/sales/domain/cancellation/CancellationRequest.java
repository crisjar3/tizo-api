package com.tizo.ecommerce.sales.domain.cancellation;

import com.tizo.ecommerce.sales.domain.order.Order;
import com.tizo.ecommerce.shared.error.DomainException;
import com.tizo.ecommerce.shared.money.Money;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record CancellationRequest(
        String id,
        String orderId,
        String status,
        String reasonCode,
        String reasonNote,
        String requestedByType,
        String requestedById,
        OffsetDateTime requestedAt,
        OffsetDateTime updatedAt,
        long version,
        List<Item> items) {

    public CancellationRequest {
        items = List.copyOf(items);
    }

    public static CancellationRequest pending(
            String requestId,
            Order order,
            Set<String> requestedItemIds,
            String reasonCode,
            String reasonNote,
            String actorType,
            String actorId,
            long expectedOrderVersion,
            OffsetDateTime now) {
        if (order.version() != expectedOrderVersion) {
            throw DomainException.conflict("STALE_ORDER_VERSION",
                    "El pedido cambió; actualice la vista antes de volver a intentar.");
        }
        if (order.dispatchedAt() != null || "DISPATCHED".equals(order.status())
                || "DELIVERED".equals(order.status())) {
            throw DomainException.validation("ORDER_ALREADY_DISPATCHED",
                    "No se puede solicitar una cancelación después del despacho.");
        }
        if (requestedItemIds == null || requestedItemIds.isEmpty()) {
            throw DomainException.validation("CANCELLATION_ITEMS_REQUIRED",
                    "Seleccione al menos una línea completa del pedido.");
        }

        Set<String> uniqueIds = new LinkedHashSet<>(requestedItemIds);
        List<Order.Item> selected = order.items().stream()
                .filter(item -> uniqueIds.contains(item.id()))
                .toList();
        if (selected.size() != uniqueIds.size()) {
            throw DomainException.validation("CANCELLATION_ITEM_NOT_FOUND",
                    "Una o más líneas no pertenecen al pedido.");
        }
        if (selected.stream().anyMatch(item -> !isCancellable(item))) {
            throw DomainException.validation("ITEM_NOT_CANCELLABLE",
                    "Una o más líneas ya no pueden cancelarse.");
        }

        List<Item> items = selected.stream()
                .map(item -> new Item(item.id(), item.quantity(), item.activeAmount()))
                .toList();
        return new CancellationRequest(
                requestId,
                order.id(),
                "PENDING",
                reasonCode,
                reasonNote,
                actorType,
                actorId,
                now,
                now,
                0,
                items);
    }

    public Money affectedAmount() {
        return items.stream()
                .map(Item::amount)
                .reduce(Money.ars(0), Money::add);
    }

    private static boolean isCancellable(Order.Item item) {
        return switch (item.customerStatus()) {
            case "CONFIRMED", "PREPARING" -> true;
            default -> false;
        };
    }

    public record Item(String orderItemId, int quantity, Money amount) {
    }
}
