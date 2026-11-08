package com.ecommerce.platform.promotion.dto;

import com.ecommerce.platform.promotion.domain.PromotionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class PromotionDtos {

    private PromotionDtos() {
    }

    public record PromotionRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 500) String description,
            @NotNull PromotionType type,
            @NotNull @Min(0) Long valueCentsOrPercent,
            Instant startsAt,
            Instant endsAt,
            @Min(1) Integer usageLimit,
            @Min(0) Long minOrderAmountCents,
            Boolean active
    ) {
    }

    public record PromotionResponse(
            UUID id,
            String code,
            String name,
            String description,
            PromotionType type,
            long valueCentsOrPercent,
            Instant startsAt,
            Instant endsAt,
            Integer usageLimit,
            int usedCount,
            long minOrderAmountCents,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ValidatePromotionRequest(
            @NotBlank @Size(max = 64) String code,
            @NotNull @Min(0) Long subtotalCents
    ) {
    }

    public record PromotionPreviewResponse(
            String code,
            long subtotalCents,
            long discountCents,
            long taxCents,
            long totalCents,
            boolean applicable
    ) {
    }
}
