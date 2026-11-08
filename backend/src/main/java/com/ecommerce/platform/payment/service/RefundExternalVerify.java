package com.ecommerce.platform.payment.service;

import com.ecommerce.platform.payment.provider.RetrievedRefund;

import java.util.Locale;

/**
 * Pure verification matchers for external refund confirmation (P7-D10). Unit-tested without Stripe I/O.
 */
public final class RefundExternalVerify {

    public enum Outcome {
        SUCCESS,
        REJECTED
    }

    public record Result(Outcome outcome, String reason) {
        public static Result success() {
            return new Result(Outcome.SUCCESS, null);
        }

        public static Result rejected(String reason) {
            return new Result(Outcome.REJECTED, reason);
        }
    }

    private RefundExternalVerify() {
    }

    public static Result verify(
            RetrievedRefund retrieved,
            String requestedProviderRefundRef,
            long expectedAmountCents,
            String expectedCurrency,
            String expectedPaymentIntentId,
            boolean stripeTestModeSecretKey) {
        if (retrieved == null) {
            return Result.rejected("Provider refund not found");
        }
        if (requestedProviderRefundRef == null
                || !requestedProviderRefundRef.equals(retrieved.id())) {
            return Result.rejected("Refund id mismatch");
        }
        if (retrieved.status() == null || !"succeeded".equalsIgnoreCase(retrieved.status())) {
            return Result.rejected("Refund status is not succeeded");
        }
        if (retrieved.amountCents() == null || retrieved.amountCents() != expectedAmountCents) {
            return Result.rejected("Refund amount mismatch");
        }
        if (retrieved.currency() == null
                || !normalizeCurrency(retrieved.currency()).equals(normalizeCurrency(expectedCurrency))) {
            return Result.rejected("Refund currency mismatch");
        }
        String resolvedPi = retrieved.paymentIntentId();
        if (resolvedPi == null || resolvedPi.isBlank()) {
            return Result.rejected("Cannot associate refund to payment intent");
        }
        if (!resolvedPi.equals(expectedPaymentIntentId)) {
            return Result.rejected("Refund payment intent mismatch");
        }
        if (retrieved.livemode() != null && retrieved.livemode() && stripeTestModeSecretKey) {
            return Result.rejected("Refund livemode inconsistent with test configuration");
        }
        return Result.success();
    }

    static String normalizeCurrency(String currency) {
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}
