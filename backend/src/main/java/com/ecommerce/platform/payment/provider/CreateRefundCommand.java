package com.ecommerce.platform.payment.provider;

public record CreateRefundCommand(String providerRef, long amountCents, String idempotencyKey) {
}
