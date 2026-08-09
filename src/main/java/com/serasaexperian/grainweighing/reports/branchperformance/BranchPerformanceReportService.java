package com.serasaexperian.grainweighing.reports.branchperformance;

import com.serasaexperian.grainweighing.reports.ReportPeriod;
import com.serasaexperian.grainweighing.weighing.BranchCostAggregate;
import com.serasaexperian.grainweighing.weighing.WeighingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;

/** MUST — o enunciado é explícito em múltiplas filiais pelo Brasil (LOG-015). */
@Service
public class BranchPerformanceReportService {

    private static final BigDecimal KG_PER_TON = BigDecimal.valueOf(1000);
    private static final int TON_SCALE = 4;
    private static final int SHARE_SCALE = 4;

    private final WeighingRepository weighingRepository;

    public BranchPerformanceReportService(WeighingRepository weighingRepository) {
        this.weighingRepository = weighingRepository;
    }

    public List<BranchPerformanceItem> query(ReportPeriod period, BranchPerformanceFilters filters) {
        List<BranchCostAggregate> aggregates = weighingRepository.aggregateByBranch(
                period.from(), period.to(), filters.grainTypeId());

        BigDecimal totalVolumeTons = aggregates.stream()
                .map(a -> toTons(a.totalNetWeightKg()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return aggregates.stream().map(a -> toItem(a, totalVolumeTons)).toList();
    }

    private BranchPerformanceItem toItem(BranchCostAggregate aggregate, BigDecimal totalVolumeTons) {
        BigDecimal volumeTons = toTons(aggregate.totalNetWeightKg());
        BigDecimal averageLoadTons = aggregate.loads() == 0
                ? BigDecimal.ZERO
                : volumeTons.divide(BigDecimal.valueOf(aggregate.loads()), TON_SCALE, RoundingMode.HALF_UP);
        BigDecimal shareOfTotalVolume = totalVolumeTons.signum() == 0
                ? BigDecimal.ZERO
                : volumeTons.divide(totalVolumeTons, SHARE_SCALE, RoundingMode.HALF_UP);

        return new BranchPerformanceItem(aggregate.branchId(), aggregate.branchName(), aggregate.loads(),
                volumeTons, aggregate.totalCost(), averageLoadTons, shareOfTotalVolume);
    }

    private BigDecimal toTons(BigDecimal weightKg) {
        return weightKg.divide(KG_PER_TON, TON_SCALE, RoundingMode.HALF_UP);
    }
}
