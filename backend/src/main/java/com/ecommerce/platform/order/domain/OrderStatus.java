package com.ecommerce.platform.order.domain;

/**
 * Order lifecycle statuses. Phase 5 reachable states for new orders are
 * {@link #PENDING_PAYMENT}, {@link #CANCELLED}, and {@link #PAYMENT_FAILED} (P5-D11).
 * {@link #PAID} and later statuses exist for forward compatibility with Phase 6+ and must not
 * be transitioned to by Phase 5 admin APIs.
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    PAYMENT_FAILED,
    CANCELLED;

    public boolean isTerminalInPhase5() {
        return this == CANCELLED || this == PAYMENT_FAILED;
    }

    public boolean isUnpaidOpen() {
        return this == PENDING_PAYMENT;
    }
}
