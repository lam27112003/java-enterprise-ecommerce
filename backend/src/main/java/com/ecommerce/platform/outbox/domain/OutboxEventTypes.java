package com.ecommerce.platform.outbox.domain;

public final class OutboxEventTypes {

    public static final String ORDER_PAID = "order.paid";
    public static final String PAYMENT_SUCCEEDED = "payment.succeeded";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String REFUND_REQUESTED = "refund.requested";

    private OutboxEventTypes() {
    }
}
