-- Phase 6: payments, refunds, transactional outbox, processed events, notifications.
-- Active-payment uniqueness is enforced in the application (H2 rejects partial unique indexes).

ALTER TABLE orders ADD COLUMN inventory_committed BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE payments (
    id                UUID PRIMARY KEY,
    order_id          UUID NOT NULL,
    user_id           UUID NOT NULL,
    status            VARCHAR(40) NOT NULL,
    amount_cents      BIGINT NOT NULL,
    currency          VARCHAR(3) NOT NULL,
    provider          VARCHAR(20) NOT NULL,
    provider_ref      VARCHAR(255),
    idempotency_key   VARCHAR(128) NOT NULL,
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payments_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uq_payments_provider_ref UNIQUE (provider_ref),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_payments_amount_nonneg CHECK (amount_cents >= 0),
    CONSTRAINT ck_payments_currency_len CHECK (char_length(currency) = 3),
    CONSTRAINT ck_payments_status CHECK (status IN (
        'PENDING_CREATE',
        'REQUIRES_ACTION',
        'PENDING',
        'CANCEL_REQUESTED',
        'CANCELED',
        'FAILED',
        'SUCCEEDED',
        'SUCCEEDED_NEEDS_REFUND',
        'CREATE_FAILED'
    ))
);

CREATE INDEX idx_payments_order_id ON payments (order_id);
CREATE INDEX idx_payments_user_id ON payments (user_id);

CREATE TABLE refunds (
    id                    UUID PRIMARY KEY,
    payment_id            UUID NOT NULL,
    order_id              UUID NOT NULL,
    status                VARCHAR(40) NOT NULL,
    reason_code           VARCHAR(64) NOT NULL,
    amount_cents          BIGINT NOT NULL,
    currency              VARCHAR(3) NOT NULL,
    provider_refund_ref   VARCHAR(255),
    idempotency_key       VARCHAR(128) NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_refunds_payment_reason UNIQUE (payment_id, reason_code),
    CONSTRAINT uq_refunds_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uq_refunds_provider_refund_ref UNIQUE (provider_refund_ref),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_refunds_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_refunds_amount_nonneg CHECK (amount_cents >= 0),
    CONSTRAINT ck_refunds_currency_len CHECK (char_length(currency) = 3),
    CONSTRAINT ck_refunds_status CHECK (status IN (
        'REFUND_PENDING',
        'REFUND_SUBMITTED',
        'REFUNDED',
        'RECONCILIATION_REQUIRED'
    ))
);

CREATE INDEX idx_refunds_order_id ON refunds (order_id);

CREATE TABLE outbox_events (
    id               UUID PRIMARY KEY,
    aggregate_type   VARCHAR(64) NOT NULL,
    aggregate_id     VARCHAR(64) NOT NULL,
    event_type       VARCHAR(80) NOT NULL,
    payload          TEXT NOT NULL,
    status           VARCHAR(20) NOT NULL,
    attempts         INT NOT NULL DEFAULT 0,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX idx_outbox_events_status_created ON outbox_events (status, created_at);

-- processed_events already exists from V1 (consumer_name, event_id).

CREATE TABLE notification_logs (
    id              UUID PRIMARY KEY,
    event_key       VARCHAR(255) NOT NULL,
    recipient       VARCHAR(255) NOT NULL,
    template_code   VARCHAR(64) NOT NULL,
    subject         VARCHAR(255) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_notification_logs_status CHECK (status IN ('SENT', 'FAILED'))
);

CREATE INDEX idx_notification_logs_event_key ON notification_logs (event_key);
