package com.ecommerce.platform.payment.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusTest {

    @Test
    void openPaymentSlotIncludesCreateAndActiveStates() {
        assertThat(PaymentStatus.PENDING_CREATE.holdsOpenPaymentSlot()).isTrue();
        assertThat(PaymentStatus.CREATE_FAILED.holdsOpenPaymentSlot()).isTrue();
        assertThat(PaymentStatus.REQUIRES_ACTION.holdsOpenPaymentSlot()).isTrue();
        assertThat(PaymentStatus.PENDING.holdsOpenPaymentSlot()).isTrue();
        assertThat(PaymentStatus.CANCEL_REQUESTED.holdsOpenPaymentSlot()).isTrue();
        assertThat(PaymentStatus.SUCCEEDED.holdsOpenPaymentSlot()).isFalse();
        assertThat(PaymentStatus.FAILED.holdsOpenPaymentSlot()).isFalse();
        assertThat(PaymentStatus.CANCELED.holdsOpenPaymentSlot()).isFalse();
        assertThat(PaymentStatus.SUCCEEDED_NEEDS_REFUND.holdsOpenPaymentSlot()).isFalse();
    }
}
