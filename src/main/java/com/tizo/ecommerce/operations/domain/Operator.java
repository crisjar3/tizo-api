package com.tizo.ecommerce.operations.domain;

public record Operator(
        String id,
        String name,
        String email,
        String avatarUrl,
        String role,
        boolean active) {
}
