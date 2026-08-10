package com.tizo.ecommerce.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TrustedClientIpResolver {

    private final Set<String> trustedProxies;

    public TrustedClientIpResolver(
            @Value("${tizo.rate-limit.trusted-proxies:127.0.0.1,::1}") String configuredProxies) {
        this.trustedProxies = Arrays.stream(configuredProxies.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (!trustedProxies.contains(remoteAddress)) {
            return remoteAddress;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remoteAddress;
        }
        String first = forwarded.split(",", 2)[0].trim();
        return isReasonableAddress(first) ? first : remoteAddress;
    }

    private boolean isReasonableAddress(String value) {
        return value.length() <= 64 && value.matches("[0-9A-Fa-f:.]{3,64}");
    }
}
