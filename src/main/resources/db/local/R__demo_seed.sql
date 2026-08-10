INSERT INTO customer (id, email, first_name, last_name, created_at)
VALUES ('customer-001', 'cliente@tizo.local', 'Cliente', 'Demo', '2026-01-01T00:00:00Z')
ON CONFLICT (id) DO NOTHING;

INSERT INTO customer_address
    (id, customer_id, recipient_name, line1, line2, city, state, postal_code, country_code, phone, is_default)
VALUES
    ('address-001', 'customer-001', 'Cliente Demo', 'Av. Corrientes 1234', 'Piso 4',
     'Buenos Aires', 'CABA', 'C1043', 'AR', '+54 11 5555 0101', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO store (id, name, active) VALUES
    ('store-001', 'Tizo Palermo', TRUE),
    ('store-002', 'Tizo Centro', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO fulfillment_hub (id, name, active)
VALUES ('hub-001', 'Hub Buenos Aires', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO operator_account (id, display_name, email, role, active, created_at) VALUES
    ('op-001', 'Ana Operaciones', 'ana.ops@tizo.local', 'SUPERVISOR', TRUE, '2026-01-01T00:00:00Z'),
    ('op-002', 'Bruno Soporte', 'bruno.ops@tizo.local', 'AGENT', TRUE, '2026-01-01T00:00:00Z'),
    ('op-inactive', 'Operador Inactivo', 'inactive@tizo.local', 'AGENT', FALSE, '2026-01-01T00:00:00Z')
ON CONFLICT (id) DO NOTHING;

INSERT INTO product
    (id, sku, name, description, category, price_amount, currency, stock, active, created_at, updated_at, version)
VALUES
    ('product-001', 'TZ-CAM-001', 'Camisa clásica', 'Camisa de algodón de corte clásico.', 'ropa', 2599000, 'ARS', 25, TRUE, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z', 0),
    ('product-002', 'TZ-PAN-001', 'Pantalón urbano', 'Pantalón urbano resistente y cómodo.', 'ropa', 3899000, 'ARS', 18, TRUE, '2026-01-02T00:00:00Z', '2026-01-02T00:00:00Z', 0),
    ('product-003', 'TZ-MOC-001', 'Mochila diaria', 'Mochila liviana para uso cotidiano.', 'accesorios', 3199000, 'ARS', 12, TRUE, '2026-01-03T00:00:00Z', '2026-01-03T00:00:00Z', 0),
    ('product-004', 'TZ-ZAP-001', 'Zapatillas paseo', 'Zapatillas versátiles para caminar.', 'calzado', 5499000, 'ARS', 8, TRUE, '2026-01-04T00:00:00Z', '2026-01-04T00:00:00Z', 0),
    ('product-005', 'TZ-GOR-001', 'Gorra esencial', 'Gorra regulable de algodón.', 'accesorios', 1499000, 'ARS', 0, TRUE, '2026-01-05T00:00:00Z', '2026-01-05T00:00:00Z', 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO product_image (product_id, url, alt_text, display_order) VALUES
    ('product-001', 'https://images.example.test/product-001/main.webp', 'Camisa clásica', 0),
    ('product-002', 'https://images.example.test/product-002/main.webp', 'Pantalón urbano', 0),
    ('product-003', 'https://images.example.test/product-003/main.webp', 'Mochila diaria', 0),
    ('product-004', 'https://images.example.test/product-004/main.webp', 'Zapatillas paseo', 0),
    ('product-005', 'https://images.example.test/product-005/main.webp', 'Gorra esencial', 0)
ON CONFLICT (product_id, display_order) DO NOTHING;

INSERT INTO product_attribute (product_id, name, value) VALUES
    ('product-001', 'material', 'algodón'),
    ('product-002', 'color', 'negro'),
    ('product-003', 'capacidad', '20L'),
    ('product-004', 'talle', '42'),
    ('product-005', 'ajuste', 'regulable')
ON CONFLICT (product_id, name) DO NOTHING;

INSERT INTO cart (id, customer_id, created_at, updated_at, version)
VALUES ('cart-customer-001', 'customer-001', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z', 0)
ON CONFLICT (customer_id) DO NOTHING;
