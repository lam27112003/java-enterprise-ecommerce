package com.ecommerce.platform.payment.domain;

public final class RefundReasons {

    public static final String LATE_SUCCESS = "LATE_SUCCESS";

    private RefundReasons() {
    }

    public static String idempotencyKey(java.util.UUID paymentId, String reasonCode) {
        return "refund:" + paymentId + ":" + reasonCode;
    }
}
