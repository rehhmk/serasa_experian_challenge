package com.serasaexperian.grainweighing.weighing;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Projeção crua de agregação por filial (LOG-015) — só soma/conta em SQL.
 * shareOfTotalVolume precisa do total do período inteiro (soma de todas as
 * filiais), então é calculado em Java pelo report service, não aqui.
 */
public record BranchCostAggregate(
        UUID branchId,
        String branchName,
        long loads,
        BigDecimal totalNetWeightKg,
        BigDecimal totalCost) {
}
