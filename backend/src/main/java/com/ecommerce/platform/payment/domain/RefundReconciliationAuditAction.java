package com.ecommerce.platform.payment.domain;

public enum RefundReconciliationAuditAction {
    CONFIRM_EXTERNAL_SUCCESS,
    CONFIRM_EXTERNAL_REJECTED,
    CONFIRM_EXTERNAL_UNCERTAIN,
    REOPEN_AND_SUBMIT,
    REOPEN_REJECTED
}
