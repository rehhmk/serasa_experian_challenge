CREATE TABLE grain_types (
    id UUID PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    purchase_price_per_ton NUMERIC(12,2) NOT NULL,
    reference_stock_kg NUMERIC(12,2) NOT NULL,
    CONSTRAINT uk_grain_types_name UNIQUE (name)
);
