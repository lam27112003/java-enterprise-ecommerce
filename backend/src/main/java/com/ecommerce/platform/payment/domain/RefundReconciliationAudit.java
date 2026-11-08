package com.ecommerce.platform.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refund_reconciliation_audits")
public class RefundReconciliationAudit {

    @Id
    private UUID id;

    @Column(name = "refund_id", nullable = false)
    private UUID refundId;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RefundReconciliationAuditAction action;

    @Column(name = "previous_status", nullable = false, length = 40)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 40)
    private String newStatus;

    @Column(name = "provider_refund_ref", length = 255)
    private String providerRefundRef;

    @Column(length = 1000)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RefundReconciliationAudit() {
    }

    public RefundReconciliationAudit(
            UUID id,
            UUID refundId,
            UUID actorUserId,
            RefundReconciliationAuditAction action,
            String previousStatus,
            String newStatus,
            String providerRefundRef,
            String note) {
        this.id = id;
        this.refundId = refundId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.providerRefundRef = providerRefundRef == null || providerRefundRef.isBlank()
                ? null
                : providerRefundRef.trim();
        this.note = note == null || note.isBlank() ? null : note.trim();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getRefundId() {
        return refundId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public RefundReconciliationAuditAction getAction() {
        return action;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public String getProviderRefundRef() {
        return providerRefundRef;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
