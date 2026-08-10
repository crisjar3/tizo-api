package com.tizo.ecommerce.shared.web;

import com.tizo.ecommerce.shared.error.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class RequestIdentityResolver {

    public static final String OPERATOR_HEADER = "X-Operator-Id";

    private final JdbcClient jdbc;
    private final boolean demoEnabled;
    private final String demoCustomerId;

    public RequestIdentityResolver(
            JdbcClient jdbc,
            @Value("${tizo.demo.enabled:false}") boolean demoEnabled,
            @Value("${tizo.demo.customer-id:customer-001}") String demoCustomerId) {
        this.jdbc = jdbc;
        this.demoEnabled = demoEnabled;
        this.demoCustomerId = demoCustomerId;
    }

    public String customerId() {
        if (!demoEnabled) {
            throw DomainException.forbidden("AUTHENTICATION_REQUIRED",
                    "El perfil productivo requiere un proveedor de identidad configurado.");
        }
        return demoCustomerId;
    }

    public String requireActiveOperator(HttpServletRequest request) {
        if (!demoEnabled) {
            throw DomainException.forbidden("AUTHENTICATION_REQUIRED",
                    "El header de operador sólo está habilitado en demo.");
        }
        String operatorId = request.getHeader(OPERATOR_HEADER);
        if (operatorId == null || operatorId.isBlank()) {
            throw DomainException.forbidden("OPERATOR_REQUIRED", "Seleccione un operador activo.");
        }
        boolean active = jdbc.sql("SELECT active FROM operator_account WHERE id = :id")
                .param("id", operatorId)
                .query(Boolean.class)
                .optional()
                .orElseThrow(() -> DomainException.forbidden("OPERATOR_INVALID", "El operador no existe."));
        if (!active) {
            throw DomainException.forbidden("OPERATOR_INACTIVE", "El operador seleccionado está inactivo.");
        }
        return operatorId;
    }
}
