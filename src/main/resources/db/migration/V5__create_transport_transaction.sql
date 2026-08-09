CREATE TABLE transport_transactions (
    id UUID PRIMARY KEY,
    truck_id UUID NOT NULL REFERENCES trucks(id),
    grain_type_id UUID NOT NULL REFERENCES grain_types(id),
    branch_id UUID NOT NULL REFERENCES branches(id),
    status VARCHAR(20) NOT NULL,
    purchase_price_snapshot NUMERIC(12,2) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ
);

CREATE INDEX idx_transport_transactions_branch_started ON transport_transactions(branch_id, started_at);
CREATE INDEX idx_transport_transactions_status_started ON transport_transactions(status, started_at);
CREATE INDEX idx_transport_transactions_truck_status ON transport_transactions(truck_id, status);
