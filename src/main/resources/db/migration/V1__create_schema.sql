CREATE TABLE product (
    id VARCHAR(64) PRIMARY KEY,
    sku VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(180) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(100) NOT NULL,
    price_amount BIGINT NOT NULL CHECK (price_amount >= 0),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'ARS'),
    stock INTEGER NOT NULL CHECK (stock >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_product_catalog ON product (active, category, created_at DESC);
CREATE INDEX idx_product_name_lower ON product (LOWER(name));

CREATE TABLE product_image (
    id BIGSERIAL PRIMARY KEY,
    product_id VARCHAR(64) NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    url VARCHAR(500) NOT NULL,
    alt_text VARCHAR(180) NOT NULL,
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    UNIQUE (product_id, display_order)
);

CREATE TABLE product_attribute (
    id BIGSERIAL PRIMARY KEY,
    product_id VARCHAR(64) NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    value VARCHAR(240) NOT NULL,
    UNIQUE (product_id, name)
);

CREATE TABLE customer (
    id VARCHAR(64) PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE customer_address (
    id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
    recipient_name VARCHAR(180) NOT NULL,
    line1 VARCHAR(240) NOT NULL,
    line2 VARCHAR(240),
    city VARCHAR(120) NOT NULL,
    state VARCHAR(120) NOT NULL,
    postal_code VARCHAR(32) NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    phone VARCHAR(40),
    is_default BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uq_customer_default_address
    ON customer_address (customer_id) WHERE is_default;

CREATE TABLE cart (
    id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL UNIQUE REFERENCES customer(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE cart_item (
    cart_id VARCHAR(64) NOT NULL REFERENCES cart(id) ON DELETE CASCADE,
    product_id VARCHAR(64) NOT NULL REFERENCES product(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    added_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (cart_id, product_id)
);

CREATE TABLE store (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE fulfillment_hub (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE customer_order (
    id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL REFERENCES customer(id),
    status VARCHAR(32) NOT NULL,
    cancellation_status VARCHAR(32) NOT NULL,
    paid_total BIGINT NOT NULL CHECK (paid_total >= 0),
    active_total BIGINT NOT NULL CHECK (active_total >= 0),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'ARS'),
    payment_method VARCHAR(40) NOT NULL,
    payment_reference VARCHAR(128),
    recipient_name VARCHAR(180) NOT NULL,
    address_line1 VARCHAR(240) NOT NULL,
    address_line2 VARCHAR(240),
    city VARCHAR(120) NOT NULL,
    state VARCHAR(120) NOT NULL,
    postal_code VARCHAR(32) NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    phone VARCHAR(40),
    store_id VARCHAR(64) REFERENCES store(id),
    hub_id VARCHAR(64) REFERENCES fulfillment_hub(id),
    dispatched_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_order_customer_created ON customer_order (customer_id, created_at DESC);
CREATE INDEX idx_order_ops_status ON customer_order (status, cancellation_status, created_at DESC);

CREATE TABLE order_item (
    id VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL REFERENCES customer_order(id) ON DELETE CASCADE,
    product_id VARCHAR(64) NOT NULL REFERENCES product(id),
    product_name VARCHAR(180) NOT NULL,
    sku VARCHAR(80) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price BIGINT NOT NULL CHECK (unit_price >= 0),
    active_amount BIGINT NOT NULL CHECK (active_amount >= 0),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'ARS'),
    status VARCHAR(32) NOT NULL,
    store_id VARCHAR(64) REFERENCES store(id),
    hub_id VARCHAR(64) REFERENCES fulfillment_hub(id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_order_item_order ON order_item (order_id);

CREATE TABLE operator_account (
    id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(180) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    role VARCHAR(40) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE cancellation_request (
    id VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL REFERENCES customer_order(id),
    status VARCHAR(32) NOT NULL,
    reason_code VARCHAR(50) NOT NULL,
    reason VARCHAR(500),
    requested_by_type VARCHAR(30) NOT NULL,
    requested_by_id VARCHAR(64) NOT NULL,
    assigned_operator_id VARCHAR(64) REFERENCES operator_account(id),
    resolved_by_operator_id VARCHAR(64) REFERENCES operator_account(id),
    rejection_code VARCHAR(50),
    operator_note VARCHAR(1000),
    invalidated_by VARCHAR(50),
    requested_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_cancellation_order ON cancellation_request (order_id, requested_at DESC);
CREATE INDEX idx_cancellation_queue ON cancellation_request (status, requested_at ASC);

CREATE TABLE cancellation_request_item (
    request_id VARCHAR(64) NOT NULL REFERENCES cancellation_request(id) ON DELETE CASCADE,
    order_item_id VARCHAR(64) NOT NULL REFERENCES order_item(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    amount BIGINT NOT NULL CHECK (amount >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (request_id, order_item_id)
);

CREATE UNIQUE INDEX uq_active_cancellation_per_item
    ON cancellation_request_item (order_item_id) WHERE active;

CREATE TABLE idempotent_operation (
    scope VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    response_status INTEGER NOT NULL,
    response_body JSONB NOT NULL,
    resource_id VARCHAR(64),
    correlation_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (scope, idempotency_key)
);

CREATE TABLE audit_event (
    id VARCHAR(64) PRIMARY KEY,
    aggregate_type VARCHAR(60) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    action VARCHAR(80) NOT NULL,
    actor_type VARCHAR(30) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_aggregate ON audit_event (aggregate_type, aggregate_id, occurred_at ASC);

CREATE TABLE refund (
    id VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL REFERENCES customer_order(id),
    cancellation_request_id VARCHAR(64) NOT NULL UNIQUE REFERENCES cancellation_request(id),
    amount BIGINT NOT NULL CHECK (amount >= 0),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'ARS'),
    status VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE operational_effect (
    id VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL REFERENCES customer_order(id),
    cancellation_request_id VARCHAR(64) NOT NULL REFERENCES cancellation_request(id),
    effect_type VARCHAR(40) NOT NULL,
    status VARCHAR(32) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ,
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (cancellation_request_id, effect_type)
);

CREATE INDEX idx_effect_work ON operational_effect (status, next_attempt_at, lease_until);
