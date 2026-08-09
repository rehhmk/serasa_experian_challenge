package com.serasaexperian.grainweighing.reports.costbygrain;

import java.math.BigDecimal;
import java.util.UUID;

public record CostByGrainItem(
        UUID grainTypeId,
        String grainName,
        long loads,
        BigDecimal volumeTons,
        BigDecimal totalCost,
        BigDecimal averageCostPerTon,
        BigDecimal averageLoadTons) {
}
