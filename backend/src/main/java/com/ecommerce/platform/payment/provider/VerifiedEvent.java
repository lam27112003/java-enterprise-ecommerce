package com.ecommerce.platform.payment.provider;

public record VerifiedEvent(
        String eventId,
        String type,
        String objectId,
        Long amountCents,
        String currency,
        String status,
        String paymentIntentId) {
}
