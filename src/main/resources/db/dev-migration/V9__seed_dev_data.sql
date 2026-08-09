-- Dev-only seed data. Loaded only when spring.flyway.locations includes classpath:db/dev-migration
-- (see application-dev.yml). Never applied against the test or a production profile.

INSERT INTO branches (id, name, city, state) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Goiania', 'Goiania', 'GO');

INSERT INTO trucks (id, plate, tare_weight_kg) VALUES
    ('22222222-2222-2222-2222-222222222222', 'ABC1D23', 9000.00);

INSERT INTO grain_types (id, name, purchase_price_per_ton, reference_stock_kg) VALUES
    ('33333333-3333-3333-3333-333333333333', 'Soja', 1800.00, 100000.00);

-- api_key_hash below is SHA-256 of the plaintext dev key "dev-scale-01-key".
-- Use that value in the X-Scale-Key header when simulating the ESP32 locally.
INSERT INTO scales (id, branch_id, api_key_hash) VALUES
    ('scale-01', '11111111-1111-1111-1111-111111111111',
     '4bedd476ab0112e6e3ba5043461d2b9927d3f69878328714e79fdedd204ef5e9');

INSERT INTO grain_stocks (id, branch_id, grain_type_id, available_quantity_kg) VALUES
    ('44444444-4444-4444-4444-444444444444',
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     20000.00);

INSERT INTO transport_transactions
    (id, truck_id, grain_type_id, branch_id, status, purchase_price_snapshot, started_at) VALUES
    ('55555555-5555-5555-5555-555555555555',
     '22222222-2222-2222-2222-222222222222',
     '33333333-3333-3333-3333-333333333333',
     '11111111-1111-1111-1111-111111111111',
     'OPEN', 1800.00, now());
