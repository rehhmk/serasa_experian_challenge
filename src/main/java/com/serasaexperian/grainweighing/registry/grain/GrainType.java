package com.serasaexperian.grainweighing.registry.grain;

import java.math.BigDecimal;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * referenceStockKg é o ponto de referência de "abundância" usado na fórmula
 * de margem (LOG-013) — assumption configurável por tipo de grão, não uma
 * verdade de negócio fixa.
 */
@Entity
@Table(name = "grain_types")
public class GrainType {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "purchase_price_per_ton", nullable = false)
    private BigDecimal purchasePricePerTon;

    @Column(name = "reference_stock_kg", nullable = false)
    private BigDecimal referenceStockKg;

    protected GrainType() {
    }

    public GrainType(UUID id, String name, BigDecimal purchasePricePerTon, BigDecimal referenceStockKg) {
        this.id = id;
        this.name = name;
        this.purchasePricePerTon = purchasePricePerTon;
        this.referenceStockKg = referenceStockKg;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPurchasePricePerTon() {
        return purchasePricePerTon;
    }

    public BigDecimal getReferenceStockKg() {
        return referenceStockKg;
    }
}
