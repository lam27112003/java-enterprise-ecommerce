-- Phase 4: inventory, adjustment audit, authenticated carts

CREATE TABLE inventory (
    product_id           UUID PRIMARY KEY,
    quantity_available   BIGINT NOT NULL DEFAULT 0,
    quantity_reserved    BIGINT NOT NULL DEFAULT 0,
    version              BIGINT NOT NULL DEFAULT 0,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_inventory_available_nonneg CHECK (quantity_available >= 0),
    CONSTRAINT ck_inventory_reserved_nonneg CHECK (quantity_reserved >= 0)
);

CREATE TABLE inventory_adjustments (
    id                         UUID PRIMARY KEY,
    product_id                 UUID NOT NULL,
    actor_user_id              UUID NOT NULL,
    delta                      BIGINT NOT NULL,
    quantity_available_before  BIGINT NOT NULL,
    quantity_available_after   BIGINT NOT NULL,
    reason                     VARCHAR(500) NOT NULL,
    created_at                 TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inv_adj_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_inv_adj_actor FOREIGN KEY (actor_user_id) REFERENCES users (id),
    CONSTRAINT ck_inv_adj_delta_nonzero CHECK (delta <> 0)
);

CREATE INDEX idx_inventory_adjustments_product_id ON inventory_adjustments (product_id);
CREATE INDEX idx_inventory_adjustments_actor_user_id ON inventory_adjustments (actor_user_id);
CREATE INDEX idx_inventory_adjustments_created_at ON inventory_adjustments (created_at);

CREATE TABLE carts (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_carts_user_id UNIQUE (user_id),
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE cart_items (
    id                UUID PRIMARY KEY,
    cart_id           UUID NOT NULL,
    product_id        UUID NOT NULL,
    quantity          INT NOT NULL,
    unit_price_cents  BIGINT NOT NULL,
    currency          VARCHAR(3) NOT NULL DEFAULT 'USD',
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cart_items_cart_product UNIQUE (cart_id, product_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_cart_items_qty_positive CHECK (quantity > 0),
    CONSTRAINT ck_cart_items_price_nonneg CHECK (unit_price_cents >= 0)
);

CREATE INDEX idx_cart_items_cart_id ON cart_items (cart_id);
CREATE INDEX idx_cart_items_product_id ON cart_items (product_id);

-- Backfill inventory for products created before Phase 4
INSERT INTO inventory (product_id, quantity_available, quantity_reserved, version)
SELECT p.id, 0, 0, 0
FROM products p
WHERE NOT EXISTS (SELECT 1 FROM inventory i WHERE i.product_id = p.id);
