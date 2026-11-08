package com.ecommerce.platform.order.service;

import com.ecommerce.platform.order.dto.OrderDtos.CheckoutRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutIdempotencyHasherTest {

    @Test
    void sameNormalizedPayloadProducesSameHashAndCaseInsensitivePromo() {
        String a = CheckoutIdempotencyHasher.hash(new CheckoutRequest("save10", " {\"a\":1} "));
        String b = CheckoutIdempotencyHasher.hash(new CheckoutRequest("SAVE10", "{\"a\":1}"));
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSize(64);

        String different = CheckoutIdempotencyHasher.hash(new CheckoutRequest("SAVE10", "{\"a\":2}"));
        assertThat(different).isNotEqualTo(a);
    }
}
