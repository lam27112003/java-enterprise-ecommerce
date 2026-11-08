package com.ecommerce.platform.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    public record InventoryResponse(
            UUID productId,
            String sku,
            String productName,
            long quantityAvailable,
            long quantityReserved,
            long version,
            Instant updatedAt
    ) {
    }

    public record InventoryAdjustRequest(
            @NotNull Long delta,
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record InventoryAdjustmentResponse(
            UUID id,
            UUID productId,
            UUID actorUserId,
            long delta,
            long quantityAvailableBefore,
            long quantityAvailableAfter,
            String reason,
            Instant createdAt
    ) {
    }
}
