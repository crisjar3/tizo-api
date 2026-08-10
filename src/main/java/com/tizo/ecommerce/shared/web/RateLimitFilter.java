package com.tizo.ecommerce.shared.web;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private final TrustedClientIpResolver clientIpResolver;
    private final boolean enabled;
    private final long limit;
    private final long capacity;
    private final Cache<String, Bucket> buckets;

    public RateLimitFilter(
            TrustedClientIpResolver clientIpResolver,
            @Value("${tizo.rate-limit.enabled:true}") boolean enabled,
            @Value("${tizo.rate-limit.requests-per-minute:60}") long limit,
            @Value("${tizo.rate-limit.burst-capacity:20}") long burst,
            @Value("${tizo.rate-limit.cache-maximum-size:10000}") long maximumSize) {
        this.clientIpResolver = clientIpResolver;
        this.enabled = enabled;
        this.limit = limit;
        this.capacity = burst > 0 ? burst : limit;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterAccess(15, TimeUnit.MINUTES)
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || request.getRequestURI().startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Bucket bucket = buckets.get(clientIpResolver.resolve(request), ignored -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        long resetSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
        response.setHeader("RateLimit-Limit", Long.toString(limit));
        response.setHeader("RateLimit-Remaining", Long.toString(probe.getRemainingTokens()));
        response.setHeader("RateLimit-Reset", Long.toString(resetSeconds));
        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(resetSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        String correlationId = com.tizo.ecommerce.shared.observability.CorrelationIdFilter.current();
        response.getWriter().write("""
                {"type":"https://api.tizo.example/problems/rate-limit-exceeded","title":"Too Many Requests","status":429,"detail":"Se excedió el límite de solicitudes.","code":"RATE_LIMIT_EXCEEDED","correlationId":"%s","error":{"category":"RATE_LIMIT","code":"RATE_LIMIT_EXCEEDED","message":"Se excedió el límite de solicitudes.","fieldErrors":[],"correlationId":"%s","retryable":true,"recoveryAction":"RETRY_LATER"}}"""
                .formatted(correlationId, correlationId));
    }

    private Bucket newBucket() {
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(limit, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(bandwidth).build();
    }
}
