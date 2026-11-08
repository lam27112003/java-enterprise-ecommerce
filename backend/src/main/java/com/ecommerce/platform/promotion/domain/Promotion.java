package com.ecommerce.platform.promotion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "promotions")
public class Promotion {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromotionType type;

    @Column(name = "value_cents_or_percent", nullable = false)
    private long valueCentsOrPercent;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "min_order_amount_cents", nullable = false)
    private long minOrderAmountCents;

    @Column(nullable = false)
    private boolean active;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Promotion() {
    }

    public Promotion(
            UUID id,
            String code,
            String name,
            PromotionType type,
            long valueCentsOrPercent) {
        this.id = id;
        this.code = code.trim().toUpperCase();
        this.name = name.trim();
        this.type = type;
        this.valueCentsOrPercent = valueCentsOrPercent;
        this.usedCount = 0;
        this.minOrderAmountCents = 0;
        this.active = true;
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

    public void incrementUsedCount() {
        if (usageLimit != null && usedCount >= usageLimit) {
            throw new IllegalStateException("Promotion usage limit reached");
        }
        usedCount++;
    }

    public void decrementUsedCount() {
        if (usedCount <= 0) {
            throw new IllegalStateException("Promotion usedCount cannot go negative");
        }
        usedCount--;
    }

    public boolean isWithinWindow(Instant now) {
        if (startsAt != null && now.isBefore(startsAt)) {
            return false;
        }
        if (endsAt != null && now.isAfter(endsAt)) {
            return false;
        }
        return true;
    }

    public boolean hasRemainingUsage() {
        return usageLimit == null || usedCount < usageLimit;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code.trim().toUpperCase();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name.trim();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PromotionType getType() {
        return type;
    }

    public void setType(PromotionType type) {
        this.type = type;
    }

    public long getValueCentsOrPercent() {
        return valueCentsOrPercent;
    }

    public void setValueCentsOrPercent(long valueCentsOrPercent) {
        this.valueCentsOrPercent = valueCentsOrPercent;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(Instant endsAt) {
        this.endsAt = endsAt;
    }

    public Integer getUsageLimit() {
        return usageLimit;
    }

    public void setUsageLimit(Integer usageLimit) {
        this.usageLimit = usageLimit;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public long getMinOrderAmountCents() {
        return minOrderAmountCents;
    }

    public void setMinOrderAmountCents(long minOrderAmountCents) {
        this.minOrderAmountCents = minOrderAmountCents;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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
