-- Phase 7 (P7-D13 / §7): payment-period revenue timestamps
-- Backfill uses updated_at as approximate completion time for historical Phase 6 rows.

ALTER TABLE payments ADD COLUMN succeeded_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE refunds ADD COLUMN refunded_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE orders ADD COLUMN paid_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_payments_succeeded_at ON payments (succeeded_at);
CREATE INDEX idx_refunds_refunded_at ON refunds (refunded_at);

UPDATE payments
   SET succeeded_at = updated_at
 WHERE status IN ('SUCCEEDED', 'SUCCEEDED_NEEDS_REFUND')
   AND succeeded_at IS NULL;

UPDATE refunds
   SET refunded_at = updated_at
 WHERE status = 'REFUNDED'
   AND refunded_at IS NULL;

UPDATE orders o
   SET paid_at = (
       SELECT MIN(p.succeeded_at)
         FROM payments p
        WHERE p.order_id = o.id
          AND p.status IN ('SUCCEEDED', 'SUCCEEDED_NEEDS_REFUND')
          AND p.succeeded_at IS NOT NULL
   )
 WHERE o.status = 'PAID'
   AND paid_at IS NULL;
