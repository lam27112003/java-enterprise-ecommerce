package com.ecommerce.platform.promotion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "promotion_redemptions")
public class PromotionRedemption {

    @Id
    private UUID id;

    @Column(name = "promotion_id", nullable = false)
    private UUID promotionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(nullable = false)
    private boolean restored;

    @Column(name = "redeemed_at", nullable = false)
    private Instant redeemedAt;

    @Column(name = "restored_at")
    private Instant restoredAt;

    protected PromotionRedemption() {
    }

    public PromotionRedemption(UUID id, UUID promotionId, UUID userId, UUID orderId) {
        this.id = id;
        this.promotionId = promotionId;
        this.userId = userId;
        this.orderId = orderId;
        this.restored = false;
    }

    @PrePersist
    void onCreate() {
        redeemedAt = Instant.now();
    }

    public boolean markRestoredOnce() {
        if (restored) {
            return false;
        }
        restored = true;
        restoredAt = Instant.now();
        return true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPromotionId() {
        return promotionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public boolean isRestored() {
        return restored;
    }

    public Instant getRedeemedAt() {
        return redeemedAt;
    }

    public Instant getRestoredAt() {
        return restoredAt;
    }
}
