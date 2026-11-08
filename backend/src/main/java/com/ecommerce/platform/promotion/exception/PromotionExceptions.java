package com.ecommerce.platform.promotion.exception;

import com.ecommerce.platform.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class PromotionExceptions {

    private PromotionExceptions() {
    }

    public static BusinessException notFound() {
        return new BusinessException("PROMOTION_NOT_FOUND", "Promotion was not found", HttpStatus.NOT_FOUND);
    }

    public static BusinessException invalid() {
        return new BusinessException("INVALID_PROMOTION", "Promotion is invalid", HttpStatus.BAD_REQUEST);
    }

    public static BusinessException inactiveOrExpired() {
        return new BusinessException(
                "PROMOTION_NOT_APPLICABLE",
                "Promotion is inactive, expired, or not yet started",
                HttpStatus.CONFLICT);
    }

    public static BusinessException minOrderNotMet() {
        return new BusinessException(
                "PROMOTION_MIN_ORDER_NOT_MET",
                "Order subtotal does not meet the promotion minimum",
                HttpStatus.CONFLICT);
    }

    public static BusinessException usageLimitReached() {
        return new BusinessException(
                "PROMOTION_USAGE_LIMIT_REACHED",
                "Promotion usage limit has been reached",
                HttpStatus.CONFLICT);
    }

    public static BusinessException duplicateCode() {
        return new BusinessException("PROMOTION_CODE_EXISTS", "Promotion code already exists", HttpStatus.CONFLICT);
    }
}
