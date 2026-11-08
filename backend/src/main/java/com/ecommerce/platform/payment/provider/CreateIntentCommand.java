package com.ecommerce.platform.payment.provider;

import java.util.UUID;

public record CreateIntentCommand(
        UUID paymentId,
        UUID orderId,
        long amountCents,
        String currency,
        String idempotencyKey) {
}
