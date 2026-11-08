package com.ecommerce.platform.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "quantity_available", nullable = false)
    private long quantityAvailable;

    @Column(name = "quantity_reserved", nullable = false)
    private long quantityReserved;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Inventory() {
    }

    public Inventory(UUID productId) {
        this.productId = productId;
        this.quantityAvailable = 0;
        this.quantityReserved = 0;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void applyAvailableDelta(long delta) {
        long next = quantityAvailable + delta;
        if (next < 0) {
            throw new IllegalStateException("Available quantity cannot be negative");
        }
        quantityAvailable = next;
    }

    public void reserve(long quantity) {
        requirePositive(quantity);
        if (quantityAvailable < quantity) {
            throw new IllegalStateException("Insufficient available quantity");
        }
        quantityAvailable -= quantity;
        quantityReserved += quantity;
    }

    public void release(long quantity) {
        requirePositive(quantity);
        if (quantityReserved < quantity) {
            throw new IllegalStateException("Insufficient reserved quantity");
        }
        quantityReserved -= quantity;
        quantityAvailable += quantity;
    }

    public void commit(long quantity) {
        requirePositive(quantity);
        if (quantityReserved < quantity) {
            throw new IllegalStateException("Insufficient reserved quantity");
        }
        quantityReserved -= quantity;
    }

    private static void requirePositive(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    public UUID getProductId() {
        return productId;
    }

    public long getQuantityAvailable() {
        return quantityAvailable;
    }

    public long getQuantityReserved() {
        return quantityReserved;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
