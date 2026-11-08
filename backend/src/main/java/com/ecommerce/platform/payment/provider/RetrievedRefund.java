package com.ecommerce.platform.payment.provider;

/**
 * Normalized provider refund snapshot for reconciliation verification (P7-D10).
 */
public record RetrievedRefund(
        String id,
        String status,
        Long amountCents,
        String currency,
        String paymentIntentId,
        String chargeId,
        Boolean livemode) {
}
