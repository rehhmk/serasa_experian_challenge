CREATE TABLE trucks (
    id UUID PRIMARY KEY,
    plate VARCHAR(10) NOT NULL,
    tare_weight_kg NUMERIC(10,2) NOT NULL,
    CONSTRAINT uk_trucks_plate UNIQUE (plate)
);
