-- Phase 6 hardening: at most one open payment attempt per order.
-- Portable UNIQUE on nullable active_lock_order_id (PostgreSQL 16 + H2 MODE=PostgreSQL).
-- PostgreSQL also applies a partial unique index via classpath:db/migration/postgresql/V10.

ALTER TABLE payments ADD COLUMN active_lock_order_id UUID;

UPDATE payments
SET active_lock_order_id = order_id
WHERE status IN (
    'PENDING_CREATE',
    'CREATE_FAILED',
    'REQUIRES_ACTION',
    'PENDING',
    'CANCEL_REQUESTED'
);

CREATE UNIQUE INDEX uq_payments_active_lock_order_id
    ON payments (active_lock_order_id);
