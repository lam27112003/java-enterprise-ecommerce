package com.ecommerce.platform.promotion.service;

import com.ecommerce.platform.promotion.domain.Promotion;
import com.ecommerce.platform.promotion.domain.PromotionType;

/**
 * Integer-cents promotion math. Discount never exceeds subtotal; total never negative.
 */
public final class PromotionPricing {

    private PromotionPricing() {
    }

    public static long discountCents(Promotion promotion, long subtotalCents) {
        if (subtotalCents < 0) {
            throw new IllegalArgumentException("subtotalCents must be non-negative");
        }
        if (promotion.getType() == PromotionType.PERCENT) {
            long raw = Math.multiplyExact(subtotalCents, promotion.getValueCentsOrPercent()) / 100L;
            return Math.min(raw, subtotalCents);
        }
        return Math.min(promotion.getValueCentsOrPercent(), subtotalCents);
    }

    public static long totalCents(long subtotalCents, long discountCents, long taxCents) {
        long total = subtotalCents - discountCents + taxCents;
        if (total < 0) {
            throw new IllegalStateException("Order total cannot be negative");
        }
        return total;
    }
}
