package com.ecommerce.platform.payment.domain;

public enum PaymentStatus {
    PENDING_CREATE,
    REQUIRES_ACTION,
    PENDING,
    CANCEL_REQUESTED,
    CANCELED,
    FAILED,
    SUCCEEDED,
    SUCCEEDED_NEEDS_REFUND,
    CREATE_FAILED;

    public boolean isActiveAttempt() {
        return this == REQUIRES_ACTION || this == PENDING || this == CANCEL_REQUESTED;
    }

    /**
     * Statuses that occupy the single open-payment slot per order (DB
     * {@code active_lock_order_id} / partial unique index).
     */
    public boolean holdsOpenPaymentSlot() {
        return this == PENDING_CREATE
                || this == CREATE_FAILED
                || isActiveAttempt();
    }
}
