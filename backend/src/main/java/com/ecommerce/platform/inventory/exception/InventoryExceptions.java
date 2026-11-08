package com.ecommerce.platform.inventory.exception;

import com.ecommerce.platform.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class InventoryExceptions {

    private InventoryExceptions() {
    }

    public static BusinessException notFound() {
        return new BusinessException("INVENTORY_NOT_FOUND", "Inventory was not found", HttpStatus.NOT_FOUND);
    }

    public static BusinessException insufficientAvailable() {
        return new BusinessException(
                "INSUFFICIENT_STOCK",
                "Insufficient available inventory",
                HttpStatus.CONFLICT);
    }

    public static BusinessException insufficientReserved() {
        return new BusinessException(
                "INSUFFICIENT_RESERVED",
                "Insufficient reserved inventory",
                HttpStatus.CONFLICT);
    }

    public static BusinessException negativeAdjustment() {
        return new BusinessException(
                "INVENTORY_NEGATIVE_ADJUSTMENT",
                "Adjustment would result in negative available inventory",
                HttpStatus.CONFLICT);
    }

    public static BusinessException invalidAdjustment() {
        return new BusinessException(
                "INVALID_INVENTORY_ADJUSTMENT",
                "Inventory adjustment is invalid",
                HttpStatus.BAD_REQUEST);
    }

    public static BusinessException concurrentModification() {
        return new BusinessException(
                "INVENTORY_CONFLICT",
                "Inventory was modified concurrently; retry the operation",
                HttpStatus.CONFLICT);
    }
}
