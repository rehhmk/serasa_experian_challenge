package com.serasaexperian.grainweighing.reports.weighingbook;

import java.util.List;
import com.serasaexperian.grainweighing.reports.ReportPeriod;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * MUST — Livro de Pesagens (LOG-015). Fonte operacional e auditável de cada
 * carga; único relatório que expõe plate. Exige paginação.
 */
@Service
public class WeighingBookQueryService {

    public List<WeighingBookItem> query(ReportPeriod period, WeighingBookFilters filters, Pageable pageable) {
        // TODO LOG-015: SELECT paginado, join weighing -> transport_transaction (para
        // branch_id) / scale / grain_type, filtros from/to/branchId/grainTypeId/
        // scaleId/plate, ORDER BY recorded_at DESC.
        throw new UnsupportedOperationException("TODO LOG-015: see WeighingBookQueryService.query() comment");
    }
}
