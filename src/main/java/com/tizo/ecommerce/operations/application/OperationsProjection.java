package com.tizo.ecommerce.operations.application;

import com.tizo.ecommerce.sales.domain.order.Order;
import com.tizo.ecommerce.shared.money.Money;
import java.time.OffsetDateTime;
import java.util.List;

public final class OperationsProjection {

    private OperationsProjection() {
    }

    public record Customer(String id, String name, String email) {
    }

    public record Store(String id, String name) {
    }

    public record Hub(String id, String name) {
    }

    public record OrderItem(Order.Item item, String storeId, String storeName) {
    }

    public record OrderView(
            Order order,
            Customer customer,
            List<Store> stores,
            Hub hub,
            List<OrderItem> items,
            String activeCancellationRequestId,
            int totalItems,
            int cancelledItems) {
        public OrderView {
            stores = List.copyOf(stores);
            items = List.copyOf(items);
        }
    }

    public record Actor(String type, String id, String name) {
    }

    public record CancellationItem(
            String itemId,
            String productId,
            String productName,
            String storeId,
            String storeName,
            int quantity,
            Money unitPrice,
            Money requestedAmount,
            String currentStatus,
            boolean stillCancellable) {
    }

    public record Refund(
            String status,
            Money amount,
            String providerReference,
            OffsetDateTime updatedAt,
            String failureCode) {
    }

    public record Effect(
            String type,
            String status,
            OffsetDateTime updatedAt,
            String failureCode) {
    }

    public record Audit(
            String id,
            String action,
            Actor actor,
            OffsetDateTime occurredAt,
            String note,
            String correlationId) {
    }

    public record CancellationView(
            String id,
            String orderId,
            String orderDisplayNumber,
            String status,
            Actor requestedBy,
            Actor resolvedBy,
            OffsetDateTime requestedAt,
            OffsetDateTime resolvedAt,
            String reasonCode,
            String reasonNote,
            String rejectionCode,
            String rejectionNote,
            List<CancellationItem> items,
            Money requestedAmount,
            Money currentAffectedAmount,
            long expectedOrderVersion,
            long currentOrderVersion,
            OffsetDateTime orderDispatchedAt,
            boolean stillValid,
            String invalidatedBy,
            Refund refund,
            List<Effect> effects,
            List<Audit> audit,
            int itemCount,
            long version) {
        public CancellationView {
            items = List.copyOf(items);
            effects = List.copyOf(effects);
            audit = List.copyOf(audit);
        }
    }

    public record Page<T>(List<T> items, int page, int pageSize, long totalItems, int totalPages) {
        public Page {
            items = List.copyOf(items);
        }
    }

    public record CancellationCounts(long pending, long completed, long rejected) {
    }

    public record CancellationPage(Page<CancellationView> page, CancellationCounts counts) {
    }
}
