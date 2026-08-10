package com.serasaexperian.grainweighing.stock;

import java.math.BigDecimal;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "grain_stocks", uniqueConstraints = @UniqueConstraint(columnNames = {"branch_id", "grain_type_id"}))
public class GrainStock {

    @Id
    private UUID id;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "grain_type_id", nullable = false)
    private UUID grainTypeId;

    @Column(name = "available_quantity_kg", nullable = false)
    private BigDecimal availableQuantityKg;

    protected GrainStock() {
    }

    public GrainStock(UUID id, UUID branchId, UUID grainTypeId, BigDecimal availableQuantityKg) {
        this.id = id;
        this.branchId = branchId;
        this.grainTypeId = grainTypeId;
        this.availableQuantityKg = availableQuantityKg;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public UUID getGrainTypeId() {
        return grainTypeId;
    }

    public BigDecimal getAvailableQuantityKg() {
        return availableQuantityKg;
    }
}
