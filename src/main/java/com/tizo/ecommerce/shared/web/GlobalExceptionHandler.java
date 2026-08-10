package com.tizo.ecommerce.shared.web;

import com.tizo.ecommerce.shared.error.DomainException;
import com.tizo.ecommerce.shared.observability.CorrelationIdFilter;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_BASE = "https://api.tizo.example/problems/";

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> handleDomain(DomainException exception, HttpServletRequest request) {
        return response(
                exception.status(),
                exception.category(),
                exception.code(),
                exception.getMessage(),
                exception.retryable(),
                exception.recoveryAction(),
                exception.fieldErrors(),
                request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleBodyValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<DomainException.FieldError> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toPublicFieldError)
                .toList();
        return response(422, "VALIDATION", "VALIDATION_FAILED",
                "La solicitud contiene campos inválidos.", false, "FIX_REQUEST", fields, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> handleParameterValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request) {
        List<DomainException.FieldError> fields = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new DomainException.FieldError(
                                result.getMethodParameter().getParameterName(),
                                "INVALID_VALUE",
                                safeMessage(error.getDefaultMessage()))))
                .toList();
        return response(422, "VALIDATION", "VALIDATION_FAILED",
                "La solicitud contiene parámetros inválidos.", false, "FIX_REQUEST", fields, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> handleMalformedRequest(Exception exception, HttpServletRequest request) {
        return response(400, "VALIDATION", "MALFORMED_REQUEST",
                "La estructura o los tipos de la solicitud no son válidos.", false, "FIX_REQUEST", List.of(), request);
    }

    @ExceptionHandler({OptimisticLockException.class, OptimisticLockingFailureException.class})
    ResponseEntity<ProblemDetail> handleOptimisticLock(Exception exception, HttpServletRequest request) {
        return response(409, "CONFLICT", "STALE_VERSION",
                "El recurso cambió; actualice la vista antes de reintentar.", false, "REFRESH", List.of(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        log.warn("event=database_integrity_conflict correlationId={}", CorrelationIdFilter.current());
        return response(409, "CONFLICT", "DATA_CONFLICT",
                "La operación entra en conflicto con el estado vigente.", false, "REFRESH", List.of(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> handleNoRoute(NoResourceFoundException exception, HttpServletRequest request) {
        return response(404, "NOT_FOUND", "ENDPOINT_NOT_FOUND",
                "El endpoint solicitado no existe.", false, "NONE", List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("event=unexpected_request_failure correlationId={}", CorrelationIdFilter.current(), exception);
        return response(500, "TECHNICAL", "INTERNAL_ERROR",
                "No fue posible completar la operación.", true, "RETRY_LATER", List.of(), request);
    }

    private ResponseEntity<ProblemDetail> response(
            int status,
            String category,
            String code,
            String message,
            boolean retryable,
            String recoveryAction,
            List<DomainException.FieldError> fieldErrors,
            HttpServletRequest request) {
        HttpStatus httpStatus = HttpStatus.valueOf(status);
        String correlationId = CorrelationIdFilter.current();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(httpStatus, message);
        problem.setType(URI.create(PROBLEM_BASE + code.toLowerCase().replace('_', '-')));
        problem.setTitle(httpStatus.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("correlationId", correlationId);

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("category", category);
        error.put("code", code);
        error.put("message", message);
        error.put("fieldErrors", fieldErrors);
        error.put("correlationId", correlationId);
        error.put("retryable", retryable);
        error.put("recoveryAction", recoveryAction);
        problem.setProperty("error", error);

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private DomainException.FieldError toPublicFieldError(FieldError error) {
        return new DomainException.FieldError(error.getField(), "INVALID_VALUE", safeMessage(error.getDefaultMessage()));
    }

    private String safeMessage(String message) {
        return message == null ? "Valor inválido." : message;
    }
}
