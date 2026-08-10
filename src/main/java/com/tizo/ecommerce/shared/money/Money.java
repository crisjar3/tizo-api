package com.tizo.ecommerce.shared.money;

import java.util.Objects;

public record Money(long amount, String currency) {

    public static final String ARS = "ARS";

    public Money {
        if (amount < 0) {
            throw new IllegalArgumentException("Money amount cannot be negative");
        }
        if (!ARS.equals(currency)) {
            throw new IllegalArgumentException("Only ARS is supported");
        }
    }

    public static Money ars(long amount) {
        return new Money(amount, ARS);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(amount, other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(amount, other.amount), currency);
    }

    public Money multiply(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        return new Money(Math.multiplyExact(amount, quantity), currency);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currencies must match");
        }
    }
}
