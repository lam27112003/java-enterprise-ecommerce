package com.ecommerce.platform.catalog.exception;

import com.ecommerce.platform.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class CatalogExceptions {

    private CatalogExceptions() {
    }

    public static BusinessException categoryNotFound() {
        return new BusinessException("CATEGORY_NOT_FOUND", "Category was not found", HttpStatus.NOT_FOUND);
    }

    public static BusinessException productNotFound() {
        return new BusinessException("PRODUCT_NOT_FOUND", "Product was not found", HttpStatus.NOT_FOUND);
    }

    public static BusinessException imageNotFound() {
        return new BusinessException("PRODUCT_IMAGE_NOT_FOUND", "Product image was not found", HttpStatus.NOT_FOUND);
    }

    public static BusinessException duplicateCategorySlug() {
        return new BusinessException("CATEGORY_SLUG_EXISTS", "Category slug already exists", HttpStatus.CONFLICT);
    }

    public static BusinessException duplicateProductSku() {
        return new BusinessException("PRODUCT_SKU_EXISTS", "Product SKU already exists", HttpStatus.CONFLICT);
    }

    public static BusinessException duplicateProductSlug() {
        return new BusinessException("PRODUCT_SLUG_EXISTS", "Product slug already exists", HttpStatus.CONFLICT);
    }

    public static BusinessException categoryHasChildren() {
        return new BusinessException(
                "CATEGORY_HAS_CHILDREN",
                "Category has child categories and cannot be deleted",
                HttpStatus.CONFLICT);
    }

    public static BusinessException categoryHasProducts() {
        return new BusinessException(
                "CATEGORY_HAS_PRODUCTS",
                "Category has products and cannot be deleted",
                HttpStatus.CONFLICT);
    }

    public static BusinessException invalidParent() {
        return new BusinessException("INVALID_CATEGORY_PARENT", "Invalid category parent", HttpStatus.BAD_REQUEST);
    }

    public static BusinessException invalidImage() {
        return new BusinessException("INVALID_PRODUCT_IMAGE", "Invalid product image upload", HttpStatus.BAD_REQUEST);
    }

    public static BusinessException invalidProduct() {
        return new BusinessException("INVALID_PRODUCT", "Invalid product request", HttpStatus.BAD_REQUEST);
    }

    public static BusinessException invalidCategory() {
        return new BusinessException("INVALID_CATEGORY", "Invalid category request", HttpStatus.BAD_REQUEST);
    }
}
