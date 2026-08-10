package com.tizo.ecommerce.sales.adapter.in.web;

import com.tizo.ecommerce.generated.model.CurrencyCode;
import com.tizo.ecommerce.generated.model.CustomerAddress;
import com.tizo.ecommerce.generated.model.CustomerCancellationSummary;
import com.tizo.ecommerce.generated.model.CustomerItemStatus;
import com.tizo.ecommerce.generated.model.CustomerOrderDetail;
import com.tizo.ecommerce.generated.model.CustomerOrderItem;
import com.tizo.ecommerce.generated.model.CustomerOrderListResponse;
import com.tizo.ecommerce.generated.model.CustomerOrderSummary;
import com.tizo.ecommerce.generated.model.CustomerRefundProjection;
import com.tizo.ecommerce.generated.model.Money;
import com.tizo.ecommerce.generated.model.OrderCancellationStatus;
import com.tizo.ecommerce.generated.model.OrderStatus;
import com.tizo.ecommerce.generated.model.Pagination;
import com.tizo.ecommerce.generated.model.RefundStatus;
import com.tizo.ecommerce.sales.application.OrderQueryPort;
import com.tizo.ecommerce.sales.domain.order.Order;
import org.springframework.stereotype.Component;

@Component
public class CustomerOrderWebMapper {

    public CustomerOrderListResponse toList(OrderQueryPort.OrderPage page) {
        return new CustomerOrderListResponse(
                page.items().stream().map(this::toSummary).toList(),
                new Pagination(page.page(), page.pageSize(), page.totalItems(), page.totalPages()));
    }

    public CustomerOrderDetail toDetail(Order order) {
        CustomerOrderDetail detail = new CustomerOrderDetail()
                .id(order.id())
                .displayNumber(order.displayNumber())
                .createdAt(order.createdAt())
                .status(OrderStatus.fromValue(order.status()))
                .cancellationStatus(OrderCancellationStatus.fromValue(order.cancellationStatus()))
                .progressStatus(CustomerOrderDetail.ProgressStatusEnum.fromValue(order.progressStatus()))
                .paidTotal(money(order.paidTotal()))
                .activeTotal(money(order.activeTotal()))
                .totalItems(order.totalItems())
                .cancelledItems(order.cancelledItems())
                .items(order.items().stream().map(item -> toItem(order, item)).toList())
                .deliveryAddress(address(order.deliveryAddress()))
                .version(order.version());
        detail.setCancellation(cancellation(order.cancellation()));
        return detail;
    }

    private CustomerOrderSummary toSummary(Order order) {
        return new CustomerOrderSummary(
                order.id(),
                order.displayNumber(),
                order.createdAt(),
                OrderStatus.fromValue(order.status()),
                OrderCancellationStatus.fromValue(order.cancellationStatus()),
                CustomerOrderSummary.ProgressStatusEnum.fromValue(order.progressStatus()),
                money(order.paidTotal()),
                money(order.activeTotal()),
                order.totalItems(),
                order.cancelledItems());
    }

    private CustomerOrderItem toItem(Order order, Order.Item item) {
        return new CustomerOrderItem(
                item.id(),
                item.productId(),
                item.productName(),
                item.imageUrl(),
                item.quantity(),
                money(item.unitPrice()),
                money(item.lineTotal()),
                CustomerItemStatus.fromValue(item.customerStatus()),
                order.dispatchedAt() == null && !item.cancelled());
    }

    private CustomerAddress address(Order.Address address) {
        return new CustomerAddress(
                address.recipientName(),
                address.line1(),
                address.line2(),
                address.city(),
                address.region(),
                address.postalCode(),
                address.countryCode());
    }

    private CustomerCancellationSummary cancellation(Order.Cancellation cancellation) {
        if (cancellation == null) {
            return null;
        }
        CustomerRefundProjection refund = new CustomerRefundProjection(
                RefundStatus.fromValue(cancellation.refundStatus()),
                cancellation.refundAmount() == null ? null : money(cancellation.refundAmount()),
                cancellation.refundUpdatedAt());
        return new CustomerCancellationSummary(
                cancellation.requestId(),
                CustomerCancellationSummary.StatusEnum.fromValue(cancellation.status()),
                money(cancellation.affectedAmount()),
                cancellation.requestedAt(),
                cancellation.resolvedAt(),
                refund);
    }

    private Money money(com.tizo.ecommerce.shared.money.Money value) {
        return new Money(value.amount(), CurrencyCode.ARS);
    }
}
