package com.serasaexperian.grainweighing.reports.inventory;

import java.util.List;
import com.serasaexperian.grainweighing.stock.MarginCalculator;
import org.springframework.stereotype.Service;

/**
 * MUST — responde diretamente "identificar oportunidades de lucro" (LOG-015).
 * A margem é calculada aqui, no domínio, via MarginCalculator — nunca em SQL.
 */
@Service
public class InventoryOpportunityReportService {

    private final MarginCalculator marginCalculator;

    public InventoryOpportunityReportService(MarginCalculator marginCalculator) {
        this.marginCalculator = marginCalculator;
    }

    public List<InventoryOpportunityItem> query(InventoryOpportunityFilters filters) {
        // TODO LOG-015: buscar GrainStock (+ join GrainType) filtrando por branchId
        // opcional; para cada linha, marginCalculator.calculate(stock, referenceStock,
        // MIN_MARGIN, MAX_MARGIN) e marginCalculator.suggestedSalePricePerTon(...).
        throw new UnsupportedOperationException(
                "TODO LOG-015: see InventoryOpportunityReportService.query() comment");
    }
}
