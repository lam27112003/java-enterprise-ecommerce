package com.ecommerce.platform.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CartDtos {

    private CartDtos() {
    }

    public record AddCartItemRequest(
            @NotNull UUID productId,
            @NotNull @Min(1) @Max(999) Integer quantity
    ) {
    }

    public record UpdateCartItemRequest(
            @NotNull @Min(1) @Max(999) Integer quantity
    ) {
    }

    public record CartItemResponse(
            UUID id,
            UUID productId,
            String productName,
            String sku,
            int quantity,
            long unitPriceCents,
            String currency,
            long lineTotalCents,
            boolean available
    ) {
    }

    public record CartResponse(
            UUID id,
            UUID userId,
            List<CartItemResponse> items,
            long subtotalCents,
            String currency,
            Instant updatedAt
    ) {
    }
}
