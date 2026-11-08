package com.ecommerce.platform.catalog.dto;

import com.ecommerce.platform.catalog.domain.ProductStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProductDtos {

    private ProductDtos() {
    }

    public record ProductRequest(
            @NotNull UUID categoryId,
            @NotBlank @Size(max = 64) String sku,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 220) String slug,
            String description,
            @NotNull @Min(0) Long priceCents,
            @Size(min = 3, max = 3) String currency,
            ProductStatus status
    ) {
    }

    public record ProductImageResponse(
            UUID id,
            String url,
            String storageKey,
            String contentType,
            long sizeBytes,
            int sortOrder,
            boolean primary,
            Instant createdAt
    ) {
    }

    public record ProductResponse(
            UUID id,
            UUID categoryId,
            String categoryName,
            String categorySlug,
            String sku,
            String name,
            String slug,
            String description,
            long priceCents,
            String currency,
            ProductStatus status,
            Instant createdAt,
            Instant updatedAt,
            List<ProductImageResponse> images
    ) {
    }

    public record ProductSummaryResponse(
            UUID id,
            UUID categoryId,
            String sku,
            String name,
            String slug,
            long priceCents,
            String currency,
            ProductStatus status,
            String primaryImageUrl
    ) {
    }
}
