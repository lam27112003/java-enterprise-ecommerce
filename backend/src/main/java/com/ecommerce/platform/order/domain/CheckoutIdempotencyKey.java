package com.ecommerce.platform.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "checkout_idempotency_keys",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_checkout_idempotency_user_key",
                columnNames = {"user_id", "idempotency_key"}
        )
)
public class CheckoutIdempotencyKey {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 128)
    private String requestHash;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CheckoutIdempotencyKey() {
    }

    public CheckoutIdempotencyKey(
            UUID id,
            UUID userId,
            String idempotencyKey,
            String requestHash,
            UUID orderId) {
        if (id == null || userId == null || orderId == null) {
            throw new IllegalArgumentException("id, userId, and orderId are required");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        if (requestHash == null || requestHash.isBlank()) {
            throw new IllegalArgumentException("requestHash is required");
        }
        this.id = id;
        this.userId = userId;
        this.idempotencyKey = idempotencyKey.trim();
        this.requestHash = requestHash.trim();
        this.orderId = orderId;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
