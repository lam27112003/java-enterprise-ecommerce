package com.ecommerce.platform.payment.exception;

import com.ecommerce.platform.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class PaymentExceptions {

    private PaymentExceptions() {
    }

    /** Marker so provider parsers can rethrow without wrapping. */
    public static class Marker extends BusinessException {
        public Marker(String code, String message, HttpStatus status) {
            super(code, message, status);
        }
    }

    public static BusinessException notFound() {
        return new Marker("PAYMENT_NOT_FOUND", "Payment was not found", HttpStatus.NOT_FOUND);
    }

    public static BusinessException refundNotFound() {
        return new Marker("REFUND_NOT_FOUND", "Refund was not found", HttpStatus.NOT_FOUND);
    }

    public static BusinessException orderNotPayable() {
        return new Marker(
                "ORDER_NOT_PAYABLE",
                "Order is not awaiting payment",
                HttpStatus.CONFLICT);
    }

    public static BusinessException invalidSignature() {
        return new Marker(
                "INVALID_WEBHOOK_SIGNATURE",
                "Webhook signature is invalid",
                HttpStatus.BAD_REQUEST);
    }

    public static BusinessException invalidWebhook() {
        return new Marker("INVALID_WEBHOOK", "Webhook payload is invalid", HttpStatus.BAD_REQUEST);
    }

    public static BusinessException amountMismatch() {
        return new Marker(
                "PAYMENT_AMOUNT_MISMATCH",
                "Payment amount or currency does not match the order",
                HttpStatus.BAD_REQUEST);
    }

    public static BusinessException providerUnavailable() {
        return new Marker(
                "PAYMENT_PROVIDER_UNAVAILABLE",
                "Payment provider request failed",
                HttpStatus.BAD_GATEWAY);
    }

    public static BusinessException refundConfirmRejected(String detail) {
        String message = detail == null || detail.isBlank()
                ? "External refund confirmation rejected"
                : detail;
        return new Marker("REFUND_CONFIRM_REJECTED", message, HttpStatus.CONFLICT);
    }

    public static BusinessException refundConfirmUncertain() {
        return new Marker(
                "REFUND_CONFIRM_UNCERTAIN",
                "External refund verification is uncertain; refund status unchanged",
                HttpStatus.SERVICE_UNAVAILABLE);
    }
}
