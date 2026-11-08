package com.ecommerce.platform.order.exception;

import com.ecommerce.platform.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class OrderExceptions {

    private OrderExceptions() {
    }

    public static BusinessException notFound() {
        return new BusinessException("ORDER_NOT_FOUND", "Order was not found", HttpStatus.NOT_FOUND);
    }

    public static BusinessException emptyCart() {
        return new BusinessException("CART_EMPTY", "Cart is empty", HttpStatus.BAD_REQUEST);
    }

    public static BusinessException productUnavailable() {
        return new BusinessException(
                "PRODUCT_NOT_AVAILABLE",
                "A cart product is not available for checkout",
                HttpStatus.CONFLICT);
    }

    public static BusinessException insufficientStock() {
        return new BusinessException(
                "INSUFFICIENT_STOCK",
                "Insufficient inventory for checkout",
                HttpStatus.CONFLICT);
    }

    public static BusinessException currencyMismatch() {
        return new BusinessException(
                "CURRENCY_MISMATCH",
                "Cart contains mixed currencies",
                HttpStatus.CONFLICT);
    }

    public static BusinessException idempotencyKeyRequired() {
        return new BusinessException(
                "IDEMPOTENCY_KEY_REQUIRED",
                "Idempotency-Key header is required",
                HttpStatus.BAD_REQUEST);
    }

    public static BusinessException idempotencyConflict() {
        return new BusinessException(
                "IDEMPOTENCY_KEY_CONFLICT",
                "Idempotency-Key was reused with a different payload",
                HttpStatus.CONFLICT);
    }

    public static BusinessException illegalStatusTransition() {
        return new BusinessException(
                "ILLEGAL_ORDER_STATUS_TRANSITION",
                "Order status transition is not allowed",
                HttpStatus.CONFLICT);
    }

    public static BusinessException cancelNotAllowed() {
        return new BusinessException(
                "ORDER_CANCEL_NOT_ALLOWED",
                "Order cannot be cancelled in its current status",
                HttpStatus.CONFLICT);
    }

    public static BusinessException shippingFieldsRequired() {
        return new BusinessException(
                "ORDER_SHIPPING_FIELDS_REQUIRED",
                "trackingNumber and carrier are required when marking an order SHIPPED",
                HttpStatus.BAD_REQUEST);
    }
}
