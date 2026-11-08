-- Phase 7 (P7-D4-B): append-only refund reconciliation audits

CREATE TABLE refund_reconciliation_audits (
    id                   UUID PRIMARY KEY,
    refund_id            UUID NOT NULL,
    actor_user_id        UUID NOT NULL,
    action               VARCHAR(40) NOT NULL,
    previous_status      VARCHAR(40) NOT NULL,
    new_status           VARCHAR(40) NOT NULL,
    provider_refund_ref  VARCHAR(255),
    note                 VARCHAR(1000),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refund_recon_audit_refund FOREIGN KEY (refund_id) REFERENCES refunds (id),
    CONSTRAINT fk_refund_recon_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users (id)
);

CREATE INDEX idx_refund_recon_audit_refund_created ON refund_reconciliation_audits (refund_id, created_at);
CREATE INDEX idx_refund_recon_audit_actor_created ON refund_reconciliation_audits (actor_user_id, created_at);
CREATE INDEX idx_refund_recon_audit_action_created ON refund_reconciliation_audits (action, created_at);
