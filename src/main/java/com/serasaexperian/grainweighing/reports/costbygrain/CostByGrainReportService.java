package com.serasaexperian.grainweighing.reports.costbygrain;

import com.serasaexperian.grainweighing.reports.ReportPeriod;
import com.serasaexperian.grainweighing.weighing.GrainTypeCostAggregate;
import com.serasaexperian.grainweighing.weighing.WeighingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;

/** MUST — responde diretamente "calcular custos" do enunciado (LOG-015). */
@Service
public class CostByGrainReportService {

    private static final BigDecimal KG_PER_TON = BigDecimal.valueOf(1000);
    private static final int TON_SCALE = 4;

    private final WeighingRepository weighingRepository;

    public CostByGrainReportService(WeighingRepository weighingRepository) {
        this.weighingRepository = weighingRepository;
    }

    public List<CostByGrainItem> query(ReportPeriod period, CostByGrainFilters filters) {
        List<GrainTypeCostAggregate> aggregates = weighingRepository.aggregateByGrainType(
                period.from(), period.to(), filters.branchId());

        return aggregates.stream().map(this::toItem).toList();
    }

    private CostByGrainItem toItem(GrainTypeCostAggregate aggregate) {
        BigDecimal volumeTons = aggregate.totalNetWeightKg().divide(KG_PER_TON, TON_SCALE, RoundingMode.HALF_UP);
        BigDecimal averageCostPerTon = volumeTons.signum() == 0
                ? BigDecimal.ZERO
                : aggregate.totalCost().divide(volumeTons, 2, RoundingMode.HALF_UP);
        BigDecimal averageLoadTons = aggregate.loads() == 0
                ? BigDecimal.ZERO
                : volumeTons.divide(BigDecimal.valueOf(aggregate.loads()), TON_SCALE, RoundingMode.HALF_UP);

        return new CostByGrainItem(aggregate.grainTypeId(), aggregate.grainName(), aggregate.loads(),
                volumeTons, aggregate.totalCost(), averageCostPerTon, averageLoadTons);
    }
}
