CREATE TABLE grain_stocks (
    id UUID PRIMARY KEY,
    branch_id UUID NOT NULL REFERENCES branches(id),
    grain_type_id UUID NOT NULL REFERENCES grain_types(id),
    available_quantity_kg NUMERIC(12,2) NOT NULL DEFAULT 0,
    CONSTRAINT uk_grain_stocks_branch_grain UNIQUE (branch_id, grain_type_id)
);
