package com.tizo.ecommerce.sales.application;

public interface CancellationDecisionPort {

    DecisionReference approve(
            String requestId,
            String operatorId,
            long expectedRequestVersion,
            long expectedOrderVersion,
            String note);

    DecisionReference reject(
            String requestId,
            String operatorId,
            long expectedRequestVersion,
            String rejectionCode,
            String rejectionNote);

    record DecisionReference(String requestId, String orderId) {
    }
}
