package com.serasaexperian.grainweighing.reports.costbygrain;

import java.util.List;
import com.serasaexperian.grainweighing.reports.ReportPeriod;
import org.springframework.stereotype.Service;

/** MUST — responde diretamente "calcular custos" do enunciado (LOG-015). */
@Service
public class CostByGrainReportService {

    public List<CostByGrainItem> query(ReportPeriod period, CostByGrainFilters filters) {
        // TODO LOG-015: GROUP BY grain_type_id, agregando COUNT(*)/SUM(net_weight)/
        // SUM(cost) sobre weighing join grain_type + transport_transaction (branch_id),
        // filtro opcional branchId, filtro obrigatório from/to.
        throw new UnsupportedOperationException("TODO LOG-015: see CostByGrainReportService.query() comment");
    }
}
