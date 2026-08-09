package com.serasaexperian.grainweighing.reports.branchperformance;

import java.util.List;
import com.serasaexperian.grainweighing.reports.ReportPeriod;
import org.springframework.stereotype.Service;

/** MUST — o enunciado é explícito em múltiplas filiais pelo Brasil (LOG-015). */
@Service
public class BranchPerformanceReportService {

    public List<BranchPerformanceItem> query(ReportPeriod period, BranchPerformanceFilters filters) {
        // TODO LOG-015: GROUP BY branch_id via transport_transaction join weighing,
        // volume/custo/loads por filial + shareOfTotalVolume (volume da filial / total
        // do período), filtro opcional grainTypeId, filtro obrigatório from/to.
        throw new UnsupportedOperationException(
                "TODO LOG-015: see BranchPerformanceReportService.query() comment");
    }
}
