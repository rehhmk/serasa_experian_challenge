package com.serasaexperian.grainweighing.reports.weighingbook;

import com.serasaexperian.grainweighing.reports.ReportPeriod;
import com.serasaexperian.grainweighing.weighing.WeighingRepository;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * MUST — Livro de Pesagens (LOG-015). Fonte operacional e auditável de cada
 * carga; único relatório que expõe plate. Exige paginação.
 */
@Service
public class WeighingBookQueryService {

    private final WeighingRepository weighingRepository;

    public WeighingBookQueryService(WeighingRepository weighingRepository) {
        this.weighingRepository = weighingRepository;
    }

    public List<WeighingBookItem> query(ReportPeriod period, WeighingBookFilters filters, Pageable pageable) {
        return weighingRepository.search(period.from(), period.to(), filters.branchId(), filters.grainTypeId(),
                filters.scaleId(), filters.plate(), pageable).getContent();
    }
}
