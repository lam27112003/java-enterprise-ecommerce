package com.ecommerce.platform.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CategoryRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 180) String slug,
            @Size(max = 1000) String description,
            UUID parentId,
            Boolean active,
            Integer sortOrder
    ) {
    }

    public record CategoryResponse(
            UUID id,
            String name,
            String slug,
            String description,
            UUID parentId,
            boolean active,
            int sortOrder,
            Instant createdAt,
            Instant updatedAt,
            List<CategoryResponse> children
    ) {
    }
}
