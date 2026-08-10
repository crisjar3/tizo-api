package com.tizo.ecommerce.operations.adapter.in.web;

import com.tizo.ecommerce.generated.model.ActorType;
import com.tizo.ecommerce.generated.model.AuditAction;
import com.tizo.ecommerce.generated.model.AuditEvent;
import com.tizo.ecommerce.generated.model.CancellationActor;
import com.tizo.ecommerce.generated.model.CancellationCounts;
import com.tizo.ecommerce.generated.model.CancellationEligibility;
import com.tizo.ecommerce.generated.model.CancellationHistoryItem;
import com.tizo.ecommerce.generated.model.CancellationHistoryResponse;
import com.tizo.ecommerce.generated.model.CancellationInvalidatedBy;
import com.tizo.ecommerce.generated.model.CancellationReasonCode;
import com.tizo.ecommerce.generated.model.CancellationRequestDetail;
import com.tizo.ecommerce.generated.model.CancellationRequestItem;
import com.tizo.ecommerce.generated.model.CancellationRequestListResponse;
import com.tizo.ecommerce.generated.model.CancellationRequestStatus;
import com.tizo.ecommerce.generated.model.CancellationRequestSummary;
import com.tizo.ecommerce.generated.model.CurrencyCode;
import com.tizo.ecommerce.generated.model.CustomerAddress;
import com.tizo.ecommerce.generated.model.HubProjection;
import com.tizo.ecommerce.generated.model.Money;
import com.tizo.ecommerce.generated.model.OperationalEffect;
import com.tizo.ecommerce.generated.model.OperationalEffectStatus;
import com.tizo.ecommerce.generated.model.OperationalEffectType;
import com.tizo.ecommerce.generated.model.OperatorListResponse;
import com.tizo.ecommerce.generated.model.OperatorRole;
import com.tizo.ecommerce.generated.model.OpsCustomerSummary;
import com.tizo.ecommerce.generated.model.OpsOrderDetail;
import com.tizo.ecommerce.generated.model.OpsOrderItem;
import com.tizo.ecommerce.generated.model.OpsOrderListResponse;
import com.tizo.ecommerce.generated.model.OpsOrderSummary;
import com.tizo.ecommerce.generated.model.OrderCancellationStatus;
import com.tizo.ecommerce.generated.model.OrderItemStatus;
import com.tizo.ecommerce.generated.model.OrderStatus;
import com.tizo.ecommerce.generated.model.Pagination;
import com.tizo.ecommerce.generated.model.RefundProjection;
import com.tizo.ecommerce.generated.model.RefundStatus;
import com.tizo.ecommerce.generated.model.RejectionCode;
import com.tizo.ecommerce.generated.model.StoreProjection;
import com.tizo.ecommerce.operations.application.OperationsProjection;
import com.tizo.ecommerce.operations.domain.Operator;
import com.tizo.ecommerce.sales.domain.order.Order;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OperationsWebMapper {

    public OperatorListResponse operators(List<Operator> operators) {
        return new OperatorListResponse(operators.stream()
                .map(operator -> new com.tizo.ecommerce.generated.model.Operator(
                        operator.id(), operator.name(), operator.email(), operator.avatarUrl(),
                        OperatorRole.fromValue(operator.role()), operator.active()))
                .toList());
    }

    public OpsOrderListResponse orderList(OperationsProjection.Page<OperationsProjection.OrderView> page) {
        return new OpsOrderListResponse(
                page.items().stream().map(this::orderSummary).toList(), pagination(page));
    }

    public OpsOrderDetail orderDetail(OperationsProjection.OrderView view) {
        Order order = view.order();
        List<String> eligibleIds = eligibleItemIds(view);
        String blockedBy = order.dispatchedAt() != null
                ? "DISPATCHED"
                : view.activeCancellationRequestId() != null
                        ? "EFFECTIVE_CANCELLATION"
                        : eligibleIds.isEmpty() ? "NO_ELIGIBLE_ITEMS" : null;
        CancellationEligibility eligibility = new CancellationEligibility(
                blockedBy == null,
                blockedBy == null ? eligibleIds : List.of(),
                blockedBy == null ? null : CancellationEligibility.BlockedByEnum.fromValue(blockedBy));
        return new OpsOrderDetail(
                order.id(), order.displayNumber(), customer(view.customer()), order.createdAt(), order.updatedAt(),
                OrderStatus.fromValue(order.status()), OrderCancellationStatus.fromValue(order.cancellationStatus()),
                order.dispatchedAt(), money(order.paidTotal()), money(order.activeTotal()), view.totalItems(),
                view.cancelledItems(), order.version(), view.items().stream().map(this::orderItem).toList(),
                address(order.deliveryAddress()),
                view.stores().stream().map(store -> new StoreProjection(store.id(), store.name())).toList(),
                view.hub() == null ? null : new HubProjection(view.hub().id(), view.hub().name()),
                view.activeCancellationRequestId(), eligibility);
    }

    public CancellationRequestListResponse cancellationList(OperationsProjection.CancellationPage result) {
        return new CancellationRequestListResponse(
                result.page().items().stream().map(this::cancellationSummary).toList(),
                pagination(result.page()),
                new CancellationCounts(
                        Math.toIntExact(result.counts().pending()),
                        Math.toIntExact(result.counts().completed()),
                        Math.toIntExact(result.counts().rejected())));
    }

    public CancellationRequestDetail cancellationDetail(OperationsProjection.CancellationView view) {
        return new CancellationRequestDetail(
                view.id(), view.orderId(), view.orderDisplayNumber(),
                CancellationRequestStatus.fromValue(view.status()), actor(view.requestedBy()), actor(view.resolvedBy()),
                view.requestedAt(), view.resolvedAt(), CancellationReasonCode.fromValue(view.reasonCode()),
                view.reasonNote(), view.rejectionCode() == null ? null : RejectionCode.fromValue(view.rejectionCode()),
                "REJECTED".equals(view.status()) ? view.rejectionNote() : null,
                view.items().stream().map(this::cancellationItem).toList(), money(view.requestedAmount()),
                money(view.currentAffectedAmount()), "COMPLETED".equals(view.status()) ? view.orderId() : null,
                view.expectedOrderVersion(), view.currentOrderVersion(), view.orderDispatchedAt(), view.stillValid(),
                view.invalidatedBy() == null ? null : CancellationInvalidatedBy.fromValue(view.invalidatedBy()),
                refund(view.refund()), view.effects().stream().map(this::effect).toList(),
                view.audit().stream().map(this::audit).toList(), view.version());
    }

    public CancellationHistoryResponse history(OperationsProjection.Page<OperationsProjection.CancellationView> page) {
        return new CancellationHistoryResponse(
                page.items().stream().map(this::historyItem).toList(), pagination(page));
    }

    private OpsOrderSummary orderSummary(OperationsProjection.OrderView view) {
        Order order = view.order();
        return new OpsOrderSummary(
                order.id(), order.displayNumber(), customer(view.customer()), order.createdAt(), order.updatedAt(),
                OrderStatus.fromValue(order.status()), OrderCancellationStatus.fromValue(order.cancellationStatus()),
                order.dispatchedAt(), money(order.paidTotal()), money(order.activeTotal()), view.totalItems(),
                view.cancelledItems(), order.version());
    }

    private OpsCustomerSummary customer(OperationsProjection.Customer customer) {
        return new OpsCustomerSummary(customer.id(), customer.name(), customer.email());
    }

    private OpsOrderItem orderItem(OperationsProjection.OrderItem view) {
        Order.Item item = view.item();
        return new OpsOrderItem(
                item.id(), item.productId(), item.productName(), item.imageUrl(), view.storeId(), view.storeName(),
                item.quantity(), money(item.unitPrice()), money(item.lineTotal()), orderItemStatus(item.customerStatus()),
                cancellable(item.customerStatus()), null);
    }

    private CancellationRequestSummary cancellationSummary(OperationsProjection.CancellationView view) {
        return new CancellationRequestSummary(
                view.id(), view.orderId(), view.orderDisplayNumber(), CancellationRequestStatus.fromValue(view.status()),
                actor(view.requestedBy()), view.requestedAt(), view.resolvedAt(),
                CancellationReasonCode.fromValue(view.reasonCode()), money(view.requestedAmount()), view.itemCount());
    }

    private CancellationRequestItem cancellationItem(OperationsProjection.CancellationItem item) {
        return new CancellationRequestItem(
                item.itemId(), item.productId(), item.productName(), item.storeId(), item.storeName(), item.quantity(),
                money(item.unitPrice()), money(item.requestedAmount()), orderItemStatus(item.currentStatus()),
                item.stillCancellable());
    }

    private CancellationHistoryItem historyItem(OperationsProjection.CancellationView view) {
        return new CancellationHistoryItem(
                view.id(), view.orderId(), view.orderDisplayNumber(),
                CancellationHistoryItem.StatusEnum.fromValue(view.status()),
                CancellationReasonCode.fromValue(view.reasonCode()),
                view.rejectionCode() == null ? null : RejectionCode.fromValue(view.rejectionCode()),
                actor(view.requestedBy()), actor(view.resolvedBy()), view.requestedAt(), view.resolvedAt(),
                money(view.requestedAmount()), RefundStatus.fromValue(view.refund().status()));
    }

    private CancellationActor actor(OperationsProjection.Actor actor) {
        return actor == null ? null : new CancellationActor(
                ActorType.fromValue(actor.type()), actor.id(), actor.name());
    }

    private RefundProjection refund(OperationsProjection.Refund refund) {
        return new RefundProjection(
                RefundStatus.fromValue(refund.status()), refund.amount() == null ? null : money(refund.amount()),
                refund.providerReference(), refund.updatedAt(), refund.failureCode());
    }

    private OperationalEffect effect(OperationsProjection.Effect effect) {
        return new OperationalEffect(
                OperationalEffectType.fromValue(effect.type()), OperationalEffectStatus.fromValue(effect.status()),
                effect.updatedAt(), effect.failureCode());
    }

    private AuditEvent audit(OperationsProjection.Audit audit) {
        return new AuditEvent(
                audit.id(), AuditAction.fromValue(audit.action()), actor(audit.actor()),
                audit.occurredAt(), audit.note());
    }

    private Pagination pagination(OperationsProjection.Page<?> page) {
        return new Pagination(page.page(), page.pageSize(), page.totalItems(), page.totalPages());
    }

    private CustomerAddress address(Order.Address address) {
        return new CustomerAddress(
                address.recipientName(), address.line1(), address.line2(), address.city(), address.region(),
                address.postalCode(), address.countryCode());
    }

    private Money money(com.tizo.ecommerce.shared.money.Money money) {
        return new Money(money.amount(), CurrencyCode.ARS);
    }

    private List<String> eligibleItemIds(OperationsProjection.OrderView view) {
        return view.items().stream()
                .filter(item -> cancellable(item.item().customerStatus()))
                .map(item -> item.item().id())
                .toList();
    }

    private boolean cancellable(String status) {
        return "CONFIRMED".equals(status) || "PENDING".equals(status) || "PREPARING".equals(status);
    }

    private OrderItemStatus orderItemStatus(String status) {
        return OrderItemStatus.fromValue("CONFIRMED".equals(status) ? "PENDING" : status);
    }
}
