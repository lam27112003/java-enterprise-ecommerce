-- PostgreSQL 16 only (not loaded by H2 test profile).
-- Reinforces one open payment attempt per order via partial unique index.

CREATE UNIQUE INDEX uq_payments_one_open_attempt_per_order
    ON payments (order_id)
    WHERE status IN (
        'PENDING_CREATE',
        'CREATE_FAILED',
        'REQUIRES_ACTION',
        'PENDING',
        'CANCEL_REQUESTED'
    );
