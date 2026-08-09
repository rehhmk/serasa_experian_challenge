package com.serasaexperian.grainweighing.stock;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Projeção crua de GrainStock + GrainType (LOG-015) — margem e preço sugerido
 * são calculados em Java pelo report service, nunca em SQL (LOG-013).
 */
public record GrainStockDetail(
        UUID grainTypeId,
        String grainName,
        BigDecimal availableQuantityKg,
        BigDecimal referenceStockKg,
        BigDecimal purchasePricePerTon) {
}
