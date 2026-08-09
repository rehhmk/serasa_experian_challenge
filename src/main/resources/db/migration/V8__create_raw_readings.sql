CREATE TABLE raw_readings (
    id BIGSERIAL PRIMARY KEY,
    scale_id VARCHAR(60) NOT NULL REFERENCES scales(id),
    plate VARCHAR(10) NOT NULL,
    weight_kg NUMERIC(10,2) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_raw_readings_scale_recorded ON raw_readings(scale_id, recorded_at);
