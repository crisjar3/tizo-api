package com.tizo.ecommerce.demo.adapter;

import com.tizo.ecommerce.demo.domain.DemoScenario;
import com.tizo.ecommerce.demo.domain.DemoScenarioState;
import com.tizo.ecommerce.shared.observability.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.ObjectMapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@ConditionalOnProperty(name = "tizo.demo.enabled", havingValue = "true")
public class DemoScenarioInterceptor extends OncePerRequestFilter {

    private static final String RESET_PATH = "/api/mock/reset";

    private final DemoScenarioState scenarioState;
    private final ObjectMapper objectMapper;
    private final long slowDelayMillis;

    public DemoScenarioInterceptor(
            DemoScenarioState scenarioState,
            ObjectMapper objectMapper,
            @Value("${tizo.demo.slow-delay:250ms}") java.time.Duration slowDelay) {
        this.scenarioState = scenarioState;
        this.objectMapper = objectMapper;
        this.slowDelayMillis = slowDelay.toMillis();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return RESET_PATH.equals(path) || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        DemoScenario scenario = scenarioState.current();
        if (scenario == DemoScenario.SLOW) {
            delay();
        }
        if (scenario == DemoScenario.SERVER_ERROR) {
            writeProblem(response, request, 500, "Internal Server Error", "DEMO_SERVER_ERROR",
                    "Fallo técnico determinista del escenario demo.", true, "RETRY_LATER");
            return;
        }
        if (scenario == DemoScenario.TIMEOUT_BEFORE_COMMIT && isMutation(request)) {
            writeProblem(response, request, 504, "Gateway Timeout", "DEMO_TIMEOUT_BEFORE_COMMIT",
                    "La operación demo agotó el tiempo antes de confirmar cambios.", true, "RECONCILE");
            return;
        }
        if (scenario == DemoScenario.TIMEOUT_AFTER_COMMIT && isMutation(request)) {
            ContentCachingResponseWrapper buffered = new ContentCachingResponseWrapper(response);
            filterChain.doFilter(request, buffered);
            if (buffered.getStatus() < 400) {
                buffered.resetBuffer();
                writeProblem(buffered, request, 504, "Gateway Timeout", "DEMO_TIMEOUT_AFTER_COMMIT",
                        "La operación fue confirmada, pero su respuesta demo agotó el tiempo.",
                        true, "RECONCILE");
            }
            buffered.copyBodyToResponse();
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isMutation(HttpServletRequest request) {
        return switch (request.getMethod().toUpperCase(Locale.ROOT)) {
            case "POST", "PUT", "PATCH", "DELETE" -> true;
            default -> false;
        };
    }

    private void delay() throws ServletException {
        try {
            Thread.sleep(slowDelayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ServletException("Demo delay interrupted", exception);
        }
    }

    private void writeProblem(
            HttpServletResponse response,
            HttpServletRequest request,
            int status,
            String title,
            String code,
            String detail,
            boolean retryable,
            String recoveryAction) throws IOException {
        String correlationId = CorrelationIdFilter.current();
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("category", "TECHNICAL");
        error.put("code", code);
        error.put("message", detail);
        error.put("fieldErrors", List.of());
        error.put("correlationId", correlationId);
        error.put("retryable", retryable);
        error.put("recoveryAction", recoveryAction);

        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "https://api.tizo.example/problems/" + code.toLowerCase(Locale.ROOT).replace('_', '-'));
        problem.put("title", title);
        problem.put("status", status);
        problem.put("detail", detail);
        problem.put("instance", request.getRequestURI());
        problem.put("code", code);
        problem.put("correlationId", correlationId);
        problem.put("error", error);

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
