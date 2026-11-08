package com.ecommerce.platform.payment.provider;

/**
 * In-memory result of creating a PaymentIntent. {@code clientSecret} is never logged.
 */
public record CreatedIntent(String providerRef, String clientSecret, String providerStatus) {
    @Override
    public String toString() {
        return "CreatedIntent[providerRef=" + providerRef + ", providerStatus=" + providerStatus + "]";
    }
}
