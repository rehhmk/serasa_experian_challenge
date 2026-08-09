package com.serasaexperian.grainweighing.registry.truck;

import java.math.BigDecimal;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "trucks")
public class Truck {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String plate;

    @Column(name = "tare_weight_kg", nullable = false)
    private BigDecimal tareWeightKg;

    protected Truck() {
    }

    public Truck(UUID id, String plate, BigDecimal tareWeightKg) {
        this.id = id;
        this.plate = plate;
        this.tareWeightKg = tareWeightKg;
    }

    public UUID getId() {
        return id;
    }

    public String getPlate() {
        return plate;
    }

    public BigDecimal getTareWeightKg() {
        return tareWeightKg;
    }
}
