-- Phase 7 (P7-D1): order fulfillment fields + append-only status history

ALTER TABLE orders ADD COLUMN tracking_number VARCHAR(64);
ALTER TABLE orders ADD COLUMN carrier VARCHAR(64);
ALTER TABLE orders ADD COLUMN shipped_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE orders ADD COLUMN delivered_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE order_status_history (
    id               UUID PRIMARY KEY,
    order_id         UUID NOT NULL,
    previous_status  VARCHAR(30) NOT NULL,
    new_status       VARCHAR(30) NOT NULL,
    actor_user_id    UUID,
    note             VARCHAR(500),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_status_history_actor FOREIGN KEY (actor_user_id) REFERENCES users (id)
);

CREATE INDEX idx_order_status_history_order_created ON order_status_history (order_id, created_at);
CREATE INDEX idx_order_status_history_created ON order_status_history (created_at);
