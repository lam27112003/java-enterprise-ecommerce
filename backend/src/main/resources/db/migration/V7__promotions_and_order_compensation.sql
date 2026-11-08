-- Phase 5: promotions + order compensation flags for exactly-once release/restore

CREATE TABLE promotions (
    id                      UUID PRIMARY KEY,
    code                    VARCHAR(64) NOT NULL,
    name                    VARCHAR(150) NOT NULL,
    description             VARCHAR(500),
    type                    VARCHAR(20) NOT NULL,
    value_cents_or_percent  BIGINT NOT NULL,
    starts_at               TIMESTAMP WITH TIME ZONE,
    ends_at                 TIMESTAMP WITH TIME ZONE,
    usage_limit             INT,
    used_count              INT NOT NULL DEFAULT 0,
    min_order_amount_cents  BIGINT NOT NULL DEFAULT 0,
    active                  BOOLEAN NOT NULL DEFAULT TRUE,
    version                 BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_promotions_code UNIQUE (code),
    CONSTRAINT ck_promotions_type CHECK (type IN ('PERCENT', 'FIXED')),
    CONSTRAINT ck_promotions_value_nonneg CHECK (value_cents_or_percent >= 0),
    CONSTRAINT ck_promotions_used_nonneg CHECK (used_count >= 0),
    CONSTRAINT ck_promotions_min_order_nonneg CHECK (min_order_amount_cents >= 0),
    CONSTRAINT ck_promotions_usage_limit_positive CHECK (usage_limit IS NULL OR usage_limit > 0),
    CONSTRAINT ck_promotions_percent_range CHECK (
        type <> 'PERCENT' OR (value_cents_or_percent >= 0 AND value_cents_or_percent <= 100)
    )
);

CREATE INDEX idx_promotions_active ON promotions (active);
CREATE INDEX idx_promotions_window ON promotions (starts_at, ends_at);

CREATE TABLE promotion_redemptions (
    id              UUID PRIMARY KEY,
    promotion_id    UUID NOT NULL,
    user_id         UUID NOT NULL,
    order_id        UUID NOT NULL,
    restored        BOOLEAN NOT NULL DEFAULT FALSE,
    redeemed_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    restored_at     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_promotion_redemptions_order UNIQUE (order_id),
    CONSTRAINT fk_promo_redemptions_promo FOREIGN KEY (promotion_id) REFERENCES promotions (id),
    CONSTRAINT fk_promo_redemptions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_promo_redemptions_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE INDEX idx_promotion_redemptions_promotion_id ON promotion_redemptions (promotion_id);
CREATE INDEX idx_promotion_redemptions_user_id ON promotion_redemptions (user_id);

ALTER TABLE orders ADD COLUMN inventory_released BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN promotion_usage_restored BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE orders ADD CONSTRAINT fk_orders_promotion FOREIGN KEY (promotion_id) REFERENCES promotions (id);
