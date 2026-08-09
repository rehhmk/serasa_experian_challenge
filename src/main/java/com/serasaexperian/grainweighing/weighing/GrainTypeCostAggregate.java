package com.serasaexperian.grainweighing.weighing;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Projeção crua de agregação por tipo de grão (LOG-015) — só soma/conta em SQL.
 * Razões derivadas (custo médio/ton, carga média) são calculadas em Java pelo
 * report service, não aqui, mesmo princípio já aplicado à margem (LOG-013).
 */
public record GrainTypeCostAggregate(
        UUID grainTypeId,
        String grainName,
        long loads,
        BigDecimal totalNetWeightKg,
        BigDecimal totalCost) {
}
