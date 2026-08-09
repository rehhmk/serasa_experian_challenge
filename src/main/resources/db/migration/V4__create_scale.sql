CREATE TABLE scales (
    id VARCHAR(60) PRIMARY KEY,
    branch_id UUID NOT NULL REFERENCES branches(id),
    api_key_hash VARCHAR(64) NOT NULL
);

CREATE INDEX idx_scales_branch_id ON scales(branch_id);
