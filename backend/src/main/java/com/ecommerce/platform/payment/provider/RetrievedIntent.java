package com.ecommerce.platform.payment.provider;

public record RetrievedIntent(
        String providerRef,
        String clientSecret,
        String providerStatus,
        Long amountCents,
        String currency) {
    @Override
    public String toString() {
        return "RetrievedIntent[providerRef=" + providerRef + ", providerStatus=" + providerStatus + "]";
    }
}
