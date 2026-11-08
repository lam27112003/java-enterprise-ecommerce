-- Phase 5.1: orders domain foundation (no payments / no promotions table yet)

CREATE TABLE orders (
    id                      UUID PRIMARY KEY,
    order_number            VARCHAR(32) NOT NULL,
    user_id                 UUID NOT NULL,
    status                  VARCHAR(30) NOT NULL,
    subtotal_cents          BIGINT NOT NULL,
    discount_cents          BIGINT NOT NULL DEFAULT 0,
    tax_cents               BIGINT NOT NULL DEFAULT 0,
    total_cents             BIGINT NOT NULL,
    currency                VARCHAR(3) NOT NULL DEFAULT 'USD',
    shipping_address_json   TEXT,
    promotion_id            UUID,
    promotion_code          VARCHAR(64),
    version                 BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_orders_order_number UNIQUE (order_number),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_orders_status CHECK (status IN (
        'PENDING_PAYMENT',
        'PAID',
        'PROCESSING',
        'SHIPPED',
        'DELIVERED',
        'PAYMENT_FAILED',
        'CANCELLED'
    )),
    CONSTRAINT ck_orders_subtotal_nonneg CHECK (subtotal_cents >= 0),
    CONSTRAINT ck_orders_discount_nonneg CHECK (discount_cents >= 0),
    CONSTRAINT ck_orders_tax_nonneg CHECK (tax_cents >= 0),
    CONSTRAINT ck_orders_total_nonneg CHECK (total_cents >= 0),
    CONSTRAINT ck_orders_currency_len CHECK (char_length(currency) = 3)
);

CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_created_at ON orders (created_at);
CREATE INDEX idx_orders_promotion_id ON orders (promotion_id);

CREATE TABLE order_items (
    id                  UUID PRIMARY KEY,
    order_id            UUID NOT NULL,
    product_id          UUID NOT NULL,
    product_name        VARCHAR(200) NOT NULL,
    sku                 VARCHAR(64) NOT NULL,
    unit_price_cents    BIGINT NOT NULL,
    quantity            INT NOT NULL,
    line_total_cents    BIGINT NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_order_items_qty_positive CHECK (quantity > 0),
    CONSTRAINT ck_order_items_unit_price_nonneg CHECK (unit_price_cents >= 0),
    CONSTRAINT ck_order_items_line_total_nonneg CHECK (line_total_cents >= 0),
    CONSTRAINT ck_order_items_line_total_matches CHECK (line_total_cents = unit_price_cents * quantity)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_product_id ON order_items (product_id);

-- Checkout idempotency (P5-D3): unique per customer key
CREATE TABLE checkout_idempotency_keys (
    id                  UUID PRIMARY KEY,
    user_id             UUID NOT NULL,
    idempotency_key     VARCHAR(128) NOT NULL,
    request_hash        VARCHAR(128) NOT NULL,
    order_id            UUID NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_checkout_idempotency_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_checkout_idempotency_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_checkout_idempotency_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE INDEX idx_checkout_idempotency_order_id ON checkout_idempotency_keys (order_id);
