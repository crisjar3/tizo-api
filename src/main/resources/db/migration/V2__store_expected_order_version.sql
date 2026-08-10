ALTER TABLE cancellation_request
    ADD COLUMN expected_order_version BIGINT NOT NULL DEFAULT 0
        CHECK (expected_order_version >= 0);
