package com.serasaexperian.grainweighing.weighing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "weighings")
public class Weighing {

    @Id
    private UUID id;

    @Column(name = "transport_transaction_id", nullable = false, unique = true)
    private UUID transportTransactionId;

    @Column(name = "scale_id", nullable = false)
    private String scaleId;

    @Column(nullable = false)
    private String plate;

    @Column(name = "gross_weight_kg", nullable = false)
    private BigDecimal grossWeightKg;

    @Column(name = "tare_weight_kg", nullable = false)
    private BigDecimal tareWeightKg;

    @Column(name = "net_weight_kg", nullable = false)
    private BigDecimal netWeightKg;

    @Column(name = "grain_type_id", nullable = false)
    private UUID grainTypeId;

    @Column(nullable = false)
    private BigDecimal cost;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "samples_used", nullable = false)
    private int samplesUsed;

    @Column(name = "standard_deviation", nullable = false)
    private BigDecimal standardDeviation;

    protected Weighing() {
    }

    public Weighing(UUID id, UUID transportTransactionId, String scaleId, String plate,
                     BigDecimal grossWeightKg, BigDecimal tareWeightKg, BigDecimal netWeightKg,
                     UUID grainTypeId, BigDecimal cost, Instant recordedAt,
                     int samplesUsed, BigDecimal standardDeviation) {
        this.id = id;
        this.transportTransactionId = transportTransactionId;
        this.scaleId = scaleId;
        this.plate = plate;
        this.grossWeightKg = grossWeightKg;
        this.tareWeightKg = tareWeightKg;
        this.netWeightKg = netWeightKg;
        this.grainTypeId = grainTypeId;
        this.cost = cost;
        this.recordedAt = recordedAt;
        this.samplesUsed = samplesUsed;
        this.standardDeviation = standardDeviation;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransportTransactionId() {
        return transportTransactionId;
    }

    public String getScaleId() {
        return scaleId;
    }

    public String getPlate() {
        return plate;
    }

    public BigDecimal getGrossWeightKg() {
        return grossWeightKg;
    }

    public BigDecimal getTareWeightKg() {
        return tareWeightKg;
    }

    public BigDecimal getNetWeightKg() {
        return netWeightKg;
    }

    public UUID getGrainTypeId() {
        return grainTypeId;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public int getSamplesUsed() {
        return samplesUsed;
    }

    public BigDecimal getStandardDeviation() {
        return standardDeviation;
    }
}
