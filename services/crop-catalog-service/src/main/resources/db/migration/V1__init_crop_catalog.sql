CREATE TABLE crops (
    id                      UUID PRIMARY KEY,
    code                    VARCHAR(64) NOT NULL,
    name                    VARCHAR(200) NOT NULL,
    scientific_name         VARCHAR(200),
    category                VARCHAR(100) NOT NULL,
    growth_days_min         INT,
    growth_days_max         INT,
    temp_min_c              NUMERIC(5,2),
    temp_max_c              NUMERIC(5,2),
    humidity_min_pct        NUMERIC(5,2),
    humidity_max_pct        NUMERIC(5,2),
    ph_min                  NUMERIC(4,2),
    ph_max                  NUMERIC(4,2),
    expected_yield_per_ha   NUMERIC(12,3),
    yield_unit              VARCHAR(32),
    description             TEXT,
    created_at              TIMESTAMP NOT NULL,
    updated_at              TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_crops_code ON crops (code);

CREATE TABLE crop_varieties (
    id              UUID PRIMARY KEY,
    crop_id         UUID NOT NULL REFERENCES crops(id),
    code            VARCHAR(64) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    origin          VARCHAR(200),
    notes           TEXT,
    created_at      TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_variety_crop_code ON crop_varieties (crop_id, code);

-- Seed sample crops for Vietnam agriculture portfolio demo
INSERT INTO crops (id, code, name, scientific_name, category, growth_days_min, growth_days_max,
                   temp_min_c, temp_max_c, humidity_min_pct, humidity_max_pct, ph_min, ph_max,
                   expected_yield_per_ha, yield_unit, description, created_at, updated_at) VALUES
('22222222-2222-2222-2222-222222222001', 'COFFEE_ROBUSTA', 'Cà phê Robusta', 'Coffea canephora', 'PERENNIAL', 900, 1200,
 18, 32, 60, 85, 5.0, 6.5, 2.5, 'TON', 'Robusta coffee for Central Highlands', NOW(), NOW()),
('22222222-2222-2222-2222-222222222002', 'DURIAN_RI6', 'Sầu riêng Ri6', 'Durio zibethinus', 'PERENNIAL', 1200, 1800,
 24, 35, 70, 90, 5.5, 6.5, 8.0, 'TON', 'Premium Ri6 durian', NOW(), NOW()),
('22222222-2222-2222-2222-222222222003', 'DRAGON_FRUIT_RED', 'Thanh long ruột đỏ', 'Hylocereus polyrhizus', 'PERENNIAL', 300, 365,
 20, 35, 50, 80, 6.0, 7.0, 25.0, 'TON', 'Red-flesh dragon fruit', NOW(), NOW()),
('22222222-2222-2222-2222-222222222004', 'RICE_ST25', 'Lúa ST25', 'Oryza sativa', 'ANNUAL', 100, 120,
 22, 34, 70, 95, 5.5, 6.5, 6.5, 'TON', 'Award-winning ST25 rice', NOW(), NOW()),
('22222222-2222-2222-2222-222222222005', 'LETTUCE', 'Rau xà lách', 'Lactuca sativa', 'ANNUAL', 30, 50,
 15, 25, 50, 70, 6.0, 7.0, 20.0, 'TON', 'Leafy lettuce for short cycles', NOW(), NOW()),
('22222222-2222-2222-2222-222222222006', 'TOMATO', 'Cà chua', 'Solanum lycopersicum', 'ANNUAL', 70, 100,
 18, 30, 50, 70, 6.0, 6.8, 40.0, 'TON', 'Table tomato', NOW(), NOW()),
('22222222-2222-2222-2222-222222222007', 'BLACK_PEPPER', 'Hồ tiêu', 'Piper nigrum', 'PERENNIAL', 700, 1000,
 23, 32, 70, 90, 5.5, 6.5, 2.0, 'TON', 'Black pepper vine', NOW(), NOW());

INSERT INTO crop_varieties (id, crop_id, code, name, origin, notes, created_at) VALUES
('33333333-3333-3333-3333-333333333001', '22222222-2222-2222-2222-222222222001', 'TR4', 'TR4 Robusta', 'Dak Lak', 'High yield clone', NOW()),
('33333333-3333-3333-3333-333333333002', '22222222-2222-2222-2222-222222222002', 'RI6', 'Ri6', 'Tien Giang', 'Sweet premium', NOW()),
('33333333-3333-3333-3333-333333333003', '22222222-2222-2222-2222-222222222004', 'ST25', 'ST25', 'Soc Trang', 'World rice contest winner lineage', NOW());
