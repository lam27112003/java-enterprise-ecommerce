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
@Table(name = "refunds")
public class Refund {

    @Id
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RefundStatus status;

    @Column(name = "reason_code", nullable = false, length = 64)
    private String reasonCode;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "provider_refund_ref", length = 255)
    private String providerRefundRef;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    protected Refund() {
    }

    public Refund(
            UUID id,
            UUID paymentId,
            UUID orderId,
            long amountCents,
            String currency,
            String reasonCode,
            String idempotencyKey) {
        if (id == null || paymentId == null || orderId == null) {
            throw new IllegalArgumentException("id, paymentId, and orderId are required");
        }
        if (amountCents < 0) {
            throw new IllegalArgumentException("amountCents must be non-negative");
        }
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-letter code");
        }
        if (reasonCode == null || reasonCode.isBlank() || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("reasonCode and idempotencyKey are required");
        }
        this.id = id;
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.status = RefundStatus.REFUND_PENDING;
        this.amountCents = amountCents;
        this.currency = currency.toUpperCase();
        this.reasonCode = reasonCode.trim();
        this.idempotencyKey = idempotencyKey.trim();
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

    public void markSubmitted() {
        this.status = RefundStatus.REFUND_SUBMITTED;
    }

    public void markRefunded(String providerRefundRef) {
        if (providerRefundRef != null && !providerRefundRef.isBlank()) {
            this.providerRefundRef = providerRefundRef.trim();
        }
        this.status = RefundStatus.REFUNDED;
        if (refundedAt == null) {
            refundedAt = Instant.now();
        }
    }

    public void markReconciliationRequired(String providerRefundRef) {
        if (providerRefundRef != null && !providerRefundRef.isBlank()) {
            this.providerRefundRef = providerRefundRef.trim();
        }
        this.status = RefundStatus.RECONCILIATION_REQUIRED;
    }

    /**
     * Ops recovery after Stripe confirms a refund already exists. Never changes {@link #idempotencyKey}.
     */
    public void confirmExternalRefund(String providerRefundRef) {
        if (this.status != RefundStatus.RECONCILIATION_REQUIRED && this.status != RefundStatus.REFUND_SUBMITTED) {
            throw new IllegalStateException("Refund cannot be confirmed from status " + status);
        }
        if (providerRefundRef == null || providerRefundRef.isBlank()) {
            throw new IllegalArgumentException("providerRefundRef is required to confirm an external refund");
        }
        markRefunded(providerRefundRef);
    }

    /**
     * Allow a single retry of Stripe refund using the same idempotency key when no provider refund exists.
     */
    public void reopenPendingForIdempotentRetry() {
        if (this.status != RefundStatus.RECONCILIATION_REQUIRED) {
            throw new IllegalStateException("Refund cannot be reopened from status " + status);
        }
        if (this.providerRefundRef != null && !this.providerRefundRef.isBlank()) {
            throw new IllegalStateException(
                    "Cannot reopen refund that already has provider_refund_ref; confirm externally instead");
        }
        this.status = RefundStatus.REFUND_PENDING;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getCurrency() {
        return currency;
    }

    public String getProviderRefundRef() {
        return providerRefundRef;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
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

    public Instant getRefundedAt() {
        return refundedAt;
    }
}
