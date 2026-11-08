package com.ecommerce.platform.payment.service;

import com.ecommerce.platform.payment.provider.RetrievedRefund;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefundExternalVerifyTest {

    @Test
    void acceptsMatchingSucceededRefund() {
        RetrievedRefund retrieved = new RetrievedRefund(
                "re_1", "succeeded", 500L, "usd", "pi_abc", null, false);
        var result = RefundExternalVerify.verify(retrieved, "re_1", 500L, "USD", "pi_abc", true);
        assertThat(result.outcome()).isEqualTo(RefundExternalVerify.Outcome.SUCCESS);
    }

    @Test
    void rejectsWrongPaymentIntent() {
        RetrievedRefund retrieved = new RetrievedRefund(
                "re_1", "succeeded", 500L, "usd", "pi_other", null, false);
        var result = RefundExternalVerify.verify(retrieved, "re_1", 500L, "USD", "pi_abc", true);
        assertThat(result.outcome()).isEqualTo(RefundExternalVerify.Outcome.REJECTED);
    }

    @Test
    void rejectsLivemodeInTestConfiguration() {
        RetrievedRefund retrieved = new RetrievedRefund(
                "re_1", "succeeded", 500L, "usd", "pi_abc", null, true);
        var result = RefundExternalVerify.verify(retrieved, "re_1", 500L, "USD", "pi_abc", true);
        assertThat(result.outcome()).isEqualTo(RefundExternalVerify.Outcome.REJECTED);
    }
}
