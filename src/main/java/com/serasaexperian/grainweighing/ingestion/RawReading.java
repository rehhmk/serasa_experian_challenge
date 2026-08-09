package com.serasaexperian.grainweighing.ingestion;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Log de auditoria/recomputabilidade — LOG-006 (revisado). Fora do caminho
 * transacional síncrono da Weighing; escrita via RawReadingBuffer/RawReadingFlusher.
 */
@Entity
@Table(name = "raw_readings")
public class RawReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scale_id", nullable = false)
    private String scaleId;

    @Column(nullable = false)
    private String plate;

    @Column(name = "weight_kg", nullable = false)
    private double weightKg;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected RawReading() {
    }

    public RawReading(String scaleId, String plate, double weightKg, Instant recordedAt) {
        this.scaleId = scaleId;
        this.plate = plate;
        this.weightKg = weightKg;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public String getScaleId() {
        return scaleId;
    }

    public String getPlate() {
        return plate;
    }

    public double getWeightKg() {
        return weightKg;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
