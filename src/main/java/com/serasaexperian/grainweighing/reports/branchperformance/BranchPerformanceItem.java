package com.serasaexperian.grainweighing.reports.branchperformance;

import java.math.BigDecimal;
import java.util.UUID;

public record BranchPerformanceItem(
        UUID branchId,
        String branchName,
        long loads,
        BigDecimal volumeTons,
        BigDecimal totalCost,
        BigDecimal averageLoadTons,
        BigDecimal shareOfTotalVolume) {
}
