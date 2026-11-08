package com.ecommerce.platform.payment.provider;

public record CancelOutcome(String providerStatus, CancelKind kind) {
}
