package com.ecommerce.platform.payment.domain;

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
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentStatus status;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_ref", length = 255)
    private String providerRef;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    /**
     * Non-null only while this row holds the open-payment slot for {@link #orderId}.
     * UNIQUE in the database so concurrent creates cannot open two attempts.
     */
    @Column(name = "active_lock_order_id")
    private UUID activeLockOrderId;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "succeeded_at")
    private Instant succeededAt;

    protected Payment() {
    }

    public Payment(
            UUID id,
            UUID orderId,
            UUID userId,
            long amountCents,
            String currency,
            String idempotencyKey) {
        if (id == null || orderId == null || userId == null) {
            throw new IllegalArgumentException("id, orderId, and userId are required");
        }
        if (amountCents < 0) {
            throw new IllegalArgumentException("amountCents must be non-negative");
        }
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-letter code");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        this.id = id;
        this.orderId = orderId;
        this.userId = userId;
        this.status = PaymentStatus.PENDING_CREATE;
        this.amountCents = amountCents;
        this.currency = currency.toUpperCase();
        this.provider = "stripe";
        this.idempotencyKey = idempotencyKey.trim();
        syncActiveLock();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        syncActiveLock();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
        syncActiveLock();
    }

    private void syncActiveLock() {
        this.activeLockOrderId = status != null && status.holdsOpenPaymentSlot() ? orderId : null;
    }

    public void assignProviderRef(String providerRef) {
        if (providerRef == null || providerRef.isBlank()) {
            throw new IllegalArgumentException("providerRef is required");
        }
        this.providerRef = providerRef.trim();
    }

    public void markRequiresAction() {
        this.status = PaymentStatus.REQUIRES_ACTION;
        syncActiveLock();
    }

    public void markPending() {
        this.status = PaymentStatus.PENDING;
        syncActiveLock();
    }

    public void markCancelRequested() {
        this.status = PaymentStatus.CANCEL_REQUESTED;
        syncActiveLock();
    }

    public void markCanceled() {
        this.status = PaymentStatus.CANCELED;
        syncActiveLock();
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
        syncActiveLock();
    }

    public void markSucceeded() {
        this.status = PaymentStatus.SUCCEEDED;
        if (succeededAt == null) {
            succeededAt = Instant.now();
        }
        syncActiveLock();
    }

    public void markSucceededNeedsRefund() {
        this.status = PaymentStatus.SUCCEEDED_NEEDS_REFUND;
        if (succeededAt == null) {
            succeededAt = Instant.now();
        }
        syncActiveLock();
    }

    public void markCreateFailed() {
        this.status = PaymentStatus.CREATE_FAILED;
        syncActiveLock();
    }

    public void reopenCreate() {
        this.status = PaymentStatus.PENDING_CREATE;
        syncActiveLock();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getUserId() {
        return userId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getCurrency() {
        return currency;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getActiveLockOrderId() {
        return activeLockOrderId;
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

    public Instant getSucceededAt() {
        return succeededAt;
    }
}
