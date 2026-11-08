package com.ecommerce.platform.cart.exception;

import com.ecommerce.platform.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class CartExceptions {

    private CartExceptions() {
    }

    public static BusinessException cartNotFound() {
        return new BusinessException("CART_NOT_FOUND", "Cart was not found", HttpStatus.NOT_FOUND);
    }

    public static BusinessException itemNotFound() {
        return new BusinessException("CART_ITEM_NOT_FOUND", "Cart item was not found", HttpStatus.NOT_FOUND);
    }

    public static BusinessException productNotAvailable() {
        return new BusinessException(
                "PRODUCT_NOT_AVAILABLE",
                "Product is not available for purchase",
                HttpStatus.CONFLICT);
    }

    public static BusinessException insufficientStock() {
        return new BusinessException(
                "INSUFFICIENT_STOCK",
                "Requested quantity exceeds available inventory",
                HttpStatus.CONFLICT);
    }

    public static BusinessException invalidQuantity() {
        return new BusinessException("INVALID_CART_QUANTITY", "Cart quantity is invalid", HttpStatus.BAD_REQUEST);
    }

    public static BusinessException accessDenied() {
        return new BusinessException("CART_ACCESS_DENIED", "Cart access denied", HttpStatus.FORBIDDEN);
    }
}
