package com.ecommerce.platform.notification.domain;

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
@Table(name = "notification_logs")
public class NotificationLog {

    public enum DeliveryStatus {
        SENT,
        FAILED
    }

    @Id
    private UUID id;

    @Column(name = "event_key", nullable = false, length = 255)
    private String eventKey;

    @Column(nullable = false, length = 255)
    private String recipient;

    @Column(name = "template_code", nullable = false, length = 64)
    private String templateCode;

    @Column(nullable = false, length = 255)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NotificationLog() {
    }

    public NotificationLog(
            UUID id,
            String eventKey,
            String recipient,
            String templateCode,
            String subject,
            DeliveryStatus status) {
        this.id = id;
        this.eventKey = eventKey;
        this.recipient = recipient;
        this.templateCode = templateCode;
        this.subject = subject;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEventKey() {
        return eventKey;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public String getSubject() {
        return subject;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
