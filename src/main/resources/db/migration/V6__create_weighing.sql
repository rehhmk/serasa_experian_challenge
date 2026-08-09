CREATE TABLE weighings (
    id UUID PRIMARY KEY,
    transport_transaction_id UUID NOT NULL REFERENCES transport_transactions(id),
    scale_id VARCHAR(60) NOT NULL REFERENCES scales(id),
    plate VARCHAR(10) NOT NULL,
    gross_weight_kg NUMERIC(10,2) NOT NULL,
    tare_weight_kg NUMERIC(10,2) NOT NULL,
    net_weight_kg NUMERIC(10,2) NOT NULL,
    grain_type_id UUID NOT NULL REFERENCES grain_types(id),
    cost NUMERIC(14,2) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    samples_used INTEGER NOT NULL,
    standard_deviation NUMERIC(10,4) NOT NULL,
    CONSTRAINT uk_weighings_transport_transaction UNIQUE (transport_transaction_id)
);

CREATE INDEX idx_weighings_recorded_at ON weighings(recorded_at);
CREATE INDEX idx_weighings_grain_recorded ON weighings(grain_type_id, recorded_at);
CREATE INDEX idx_weighings_scale_recorded ON weighings(scale_id, recorded_at);
