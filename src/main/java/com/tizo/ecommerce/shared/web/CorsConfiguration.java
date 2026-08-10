package com.tizo.ecommerce.shared.web;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class CorsConfiguration implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public CorsConfiguration(@Value("${tizo.cors.allowed-origins:}") String configuredOrigins) {
        this.allowedOrigins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Accept", "X-Correlation-ID", "X-Operator-Id")
                .exposedHeaders("Location", "X-Correlation-ID", "RateLimit-Limit",
                        "RateLimit-Remaining", "RateLimit-Reset", "Retry-After")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
