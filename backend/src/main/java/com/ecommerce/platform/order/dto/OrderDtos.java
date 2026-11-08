package com.ecommerce.platform.order.dto;

import com.ecommerce.platform.order.domain.OrderStatus;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record CheckoutRequest(
            @Size(max = 64) String promotionCode,
            String shippingAddressJson
    ) {
    }

    public record OrderItemResponse(
            UUID id,
            UUID productId,
            String productName,
            String sku,
            long unitPriceCents,
            int quantity,
            long lineTotalCents
    ) {
    }

    public record OrderResponse(
            UUID id,
            String orderNumber,
            UUID userId,
            OrderStatus status,
            long subtotalCents,
            long discountCents,
            long taxCents,
            long totalCents,
            String currency,
            String shippingAddressJson,
            String trackingNumber,
            String carrier,
            Instant shippedAt,
            Instant deliveredAt,
            Instant paidAt,
            UUID promotionId,
            String promotionCode,
            List<OrderItemResponse> items,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record OrderStatusHistoryResponse(
            UUID id,
            OrderStatus previousStatus,
            OrderStatus newStatus,
            UUID actorUserId,
            String note,
            Instant createdAt
    ) {
    }

    public record AdminStatusUpdateRequest(
            OrderStatus status,
            @Size(max = 64) String trackingNumber,
            @Size(max = 64) String carrier,
            @Size(max = 500) String note
    ) {
    }
}
