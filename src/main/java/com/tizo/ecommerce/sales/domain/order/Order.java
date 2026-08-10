package com.tizo.ecommerce.sales.domain.order;

import com.tizo.ecommerce.shared.money.Money;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

public record Order(
        String id,
        String customerId,
        String status,
        String cancellationStatus,
        Money paidTotal,
        Money activeTotal,
        String paymentMethod,
        Address deliveryAddress,
        OffsetDateTime dispatchedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version,
        List<Item> items,
        Cancellation cancellation) {

    public Order {
        items = List.copyOf(items);
    }

    public String displayNumber() {
        String compact = id.replace("-", "").toUpperCase();
        return "TZ-" + compact.substring(0, Math.min(10, compact.length()));
    }

    public int totalItems() {
        return items.stream().mapToInt(Item::quantity).sum();
    }

    public int cancelledItems() {
        return items.stream().filter(Item::cancelled).mapToInt(Item::quantity).sum();
    }

    public String progressStatus() {
        return items.stream()
                .filter(item -> !item.cancelled())
                .map(Item::customerStatus)
                .min(Comparator.comparingInt(Order::progressRank))
                .map(Order::toProgress)
                .orElse("DELIVERED");
    }

    private static int progressRank(String status) {
        return switch (status) {
            case "CONFIRMED" -> 0;
            case "PREPARING" -> 1;
            case "ON_THE_WAY" -> 2;
            case "DELIVERED" -> 3;
            default -> 4;
        };
    }

    private static String toProgress(String status) {
        return switch (status) {
            case "CONFIRMED" -> "PENDING";
            case "PREPARING" -> "PREPARING";
            case "ON_THE_WAY" -> "IN_TRANSIT_TO_HUB";
            case "DELIVERED" -> "DELIVERED";
            default -> "PENDING";
        };
    }

    public record Address(
            String recipientName,
            String line1,
            String line2,
            String city,
            String region,
            String postalCode,
            String countryCode,
            String phone) {
    }

    public record Item(
            String id,
            String productId,
            String productName,
            String sku,
            String imageUrl,
            int quantity,
            Money unitPrice,
            Money activeAmount,
            String customerStatus) {

        public Money lineTotal() {
            return unitPrice.multiply(quantity);
        }

        public boolean cancelled() {
            return "CANCELLED".equals(customerStatus);
        }
    }

    public record Cancellation(
            String requestId,
            String status,
            Money affectedAmount,
            OffsetDateTime requestedAt,
            OffsetDateTime resolvedAt,
            String refundStatus,
            Money refundAmount,
            OffsetDateTime refundUpdatedAt) {
    }
}
