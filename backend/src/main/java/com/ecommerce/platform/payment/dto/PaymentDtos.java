package com.ecommerce.platform.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    /**
     * Returned only by the pay API. {@code clientSecret} is for Stripe Elements and must not be logged.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PayResponse(
            UUID id,
            UUID orderId,
            String status,
            long amountCents,
            String currency,
            String providerRef,
            String clientSecret) {
        @Override
        public String toString() {
            return "PayResponse[id=" + id + ", orderId=" + orderId + ", status=" + status
                    + ", providerRef=" + providerRef + "]";
        }
    }

    public record PaymentResponse(
            UUID id,
            UUID orderId,
            UUID userId,
            String status,
            long amountCents,
            String currency,
            String providerRef,
            Instant createdAt,
            Instant updatedAt) {
    }
}
