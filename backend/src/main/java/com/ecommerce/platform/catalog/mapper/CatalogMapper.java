package com.ecommerce.platform.catalog.mapper;

import com.ecommerce.platform.catalog.domain.Category;
import com.ecommerce.platform.catalog.domain.Product;
import com.ecommerce.platform.catalog.domain.ProductImage;
import com.ecommerce.platform.catalog.dto.CategoryDtos.CategoryResponse;
import com.ecommerce.platform.catalog.dto.ProductDtos.ProductImageResponse;
import com.ecommerce.platform.catalog.dto.ProductDtos.ProductResponse;
import com.ecommerce.platform.catalog.dto.ProductDtos.ProductSummaryResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CatalogMapper {

    private CatalogMapper() {
    }

    public static CategoryResponse toCategory(Category category) {
        return toCategory(category, List.of());
    }

    public static CategoryResponse toCategory(Category category, List<CategoryResponse> children) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getParent() == null ? null : category.getParent().getId(),
                category.isActive(),
                category.getSortOrder(),
                category.getCreatedAt(),
                category.getUpdatedAt(),
                children
        );
    }

    public static List<CategoryResponse> toTree(List<Category> categories) {
        Map<UUID, List<Category>> childrenByParent = new HashMap<>();
        List<Category> roots = new ArrayList<>();
        for (Category category : categories) {
            if (category.getParent() == null) {
                roots.add(category);
            } else {
                childrenByParent
                        .computeIfAbsent(category.getParent().getId(), id -> new ArrayList<>())
                        .add(category);
            }
        }
        roots.sort(Comparator.comparingInt(Category::getSortOrder).thenComparing(Category::getName));
        return roots.stream().map(root -> buildNode(root, childrenByParent)).toList();
    }

    private static CategoryResponse buildNode(Category category, Map<UUID, List<Category>> childrenByParent) {
        List<Category> children = childrenByParent.getOrDefault(category.getId(), List.of());
        children = new ArrayList<>(children);
        children.sort(Comparator.comparingInt(Category::getSortOrder).thenComparing(Category::getName));
        List<CategoryResponse> childResponses = children.stream()
                .map(child -> buildNode(child, childrenByParent))
                .toList();
        return toCategory(category, childResponses);
    }

    public static ProductImageResponse toImage(ProductImage image) {
        return new ProductImageResponse(
                image.getId(),
                image.getUrl(),
                image.getStorageKey(),
                image.getContentType(),
                image.getSizeBytes(),
                image.getSortOrder(),
                image.isPrimary(),
                image.getCreatedAt()
        );
    }

    public static ProductResponse toProduct(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getCategory().getSlug(),
                product.getSku(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getPriceCents(),
                product.getCurrency(),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getImages().stream().map(CatalogMapper::toImage).toList()
        );
    }

    public static ProductSummaryResponse toSummary(Product product) {
        String primaryUrl = product.getImages().stream()
                .filter(ProductImage::isPrimary)
                .map(ProductImage::getUrl)
                .findFirst()
                .orElseGet(() -> product.getImages().stream().findFirst().map(ProductImage::getUrl).orElse(null));
        return new ProductSummaryResponse(
                product.getId(),
                product.getCategory().getId(),
                product.getSku(),
                product.getName(),
                product.getSlug(),
                product.getPriceCents(),
                product.getCurrency(),
                product.getStatus(),
                primaryUrl
        );
    }
}
