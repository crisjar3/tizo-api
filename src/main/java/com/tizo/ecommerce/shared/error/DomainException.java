package com.tizo.ecommerce.shared.error;

import java.util.List;

public class DomainException extends RuntimeException {

    private final int status;
    private final String category;
    private final String code;
    private final boolean retryable;
    private final String recoveryAction;
    private final List<FieldError> fieldErrors;

    public DomainException(
            int status,
            String category,
            String code,
            String message,
            boolean retryable,
            String recoveryAction,
            List<FieldError> fieldErrors) {
        super(message);
        this.status = status;
        this.category = category;
        this.code = code;
        this.retryable = retryable;
        this.recoveryAction = recoveryAction;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public static DomainException badRequest(String code, String message) {
        return new DomainException(400, "VALIDATION", code, message, false, "FIX_REQUEST", List.of());
    }

    public static DomainException validation(String code, String message) {
        return new DomainException(422, "VALIDATION", code, message, false, "FIX_REQUEST", List.of());
    }

    public static DomainException notFound(String code, String message) {
        return new DomainException(404, "NOT_FOUND", code, message, false, "NONE", List.of());
    }

    public static DomainException conflict(String code, String message) {
        return new DomainException(409, "CONFLICT", code, message, false, "REFRESH", List.of());
    }

    public static DomainException forbidden(String code, String message) {
        return new DomainException(403, "AUTHORIZATION", code, message, false, "SELECT_OPERATOR", List.of());
    }

    public int status() {
        return status;
    }

    public String category() {
        return category;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }

    public String recoveryAction() {
        return recoveryAction;
    }

    public List<FieldError> fieldErrors() {
        return fieldErrors;
    }

    public record FieldError(String field, String code, String message) {
    }
}
