package com.ecommerce.platform.payment.provider;

public record RefundOutcome(String providerRefundRef, String providerStatus, RefundOutcomeKind kind) {
}
