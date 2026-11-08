package com.ecommerce.platform.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_adjustments")
public class InventoryAdjustment {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(nullable = false)
    private long delta;

    @Column(name = "quantity_available_before", nullable = false)
    private long quantityAvailableBefore;

    @Column(name = "quantity_available_after", nullable = false)
    private long quantityAvailableAfter;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InventoryAdjustment() {
    }

    public InventoryAdjustment(
            UUID id,
            UUID productId,
            UUID actorUserId,
            long delta,
            long quantityAvailableBefore,
            long quantityAvailableAfter,
            String reason) {
        this.id = id;
        this.productId = productId;
        this.actorUserId = actorUserId;
        this.delta = delta;
        this.quantityAvailableBefore = quantityAvailableBefore;
        this.quantityAvailableAfter = quantityAvailableAfter;
        this.reason = reason;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public long getDelta() {
        return delta;
    }

    public long getQuantityAvailableBefore() {
        return quantityAvailableBefore;
    }

    public long getQuantityAvailableAfter() {
        return quantityAvailableAfter;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
