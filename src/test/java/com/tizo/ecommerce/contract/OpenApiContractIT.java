package com.tizo.ecommerce.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OpenApiContractIT {

    private static final Path CONTRACT = Path.of("src", "main", "openapi", "openapi.yaml");
    private static final Set<String> EXPECTED_OPERATION_IDS = Set.of(
            "listProducts", "getProduct", "getCart", "updateCartItem", "deleteCartItem",
            "createOrder", "listCustomerOrders", "reconcileCheckout", "getCustomerOrder",
            "createCustomerCancellation", "reconcileCustomerCancellation", "listOperators",
            "listOpsOrders", "getOpsOrder", "createOpsCancellation", "listCancellationRequests",
            "reconcileOpsCancellation", "getCancellationRequest", "approveCancellationRequest",
            "rejectCancellationRequest", "listCancellationHistory", "resetDemo");

    @Test
    void preservesCompatibilityRoutesHeadersAndAllOperationIds() throws IOException {
        String contract = Files.readString(CONTRACT);
        Matcher operationMatcher = Pattern.compile("(?m)^\\s{6}operationId:\\s+(\\S+)\\s*$").matcher(contract);
        Set<String> actualOperations = operationMatcher.results()
                .map(result -> result.group(1))
                .collect(Collectors.toUnmodifiableSet());

        assertThat(actualOperations).containsExactlyInAnyOrderElementsOf(EXPECTED_OPERATION_IDS);
        assertThat(actualOperations).hasSize(22);
        assertThat(contract).contains(
                "  /api/catalog/products:",
                "  /api/me/cart:",
                "  /api/me/orders:",
                "  /api/ops/orders:",
                "  /api/ops/cancellation-requests:",
                "  /api/mock/reset:",
                "idempotencyKey:",
                "name: X-Operator-Id",
                "X-Correlation-Id:",
                "RateLimit-Limit:",
                "RateLimit-Remaining:",
                "RateLimit-Reset:",
                "application/problem+json");
        assertThat(contract).doesNotContain("  /api/v1/");
    }
}
