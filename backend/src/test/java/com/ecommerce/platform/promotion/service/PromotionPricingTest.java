package com.ecommerce.platform.promotion.service;

import com.ecommerce.platform.promotion.domain.Promotion;
import com.ecommerce.platform.promotion.domain.PromotionType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PromotionPricingTest {

    @Test
    void percentAndFixedNeverExceedSubtotalOrGoNegative() {
        Promotion percent = new Promotion(UUID.randomUUID(), "P10", "Ten", PromotionType.PERCENT, 10);
        assertThat(PromotionPricing.discountCents(percent, 1999)).isEqualTo(199);
        assertThat(PromotionPricing.totalCents(1999, 199, 0)).isEqualTo(1800);

        Promotion fixed = new Promotion(UUID.randomUUID(), "F50", "Fifty", PromotionType.FIXED, 5000);
        assertThat(PromotionPricing.discountCents(fixed, 1999)).isEqualTo(1999);
        assertThat(PromotionPricing.totalCents(1999, 1999, 0)).isZero();
    }
}
