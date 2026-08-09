package com.serasaexperian.grainweighing.reports.inventory;

import com.serasaexperian.grainweighing.stock.GrainStockDetail;
import com.serasaexperian.grainweighing.stock.GrainStockRepository;
import com.serasaexperian.grainweighing.stock.MarginCalculator;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * MUST — responde diretamente "identificar oportunidades de lucro" (LOG-015).
 * A margem é calculada aqui, no domínio, via MarginCalculator — nunca em SQL.
 */
@Service
public class InventoryOpportunityReportService {

    /** Limites fixos do enunciado (5%-20%), não uma assumption calibrável — ver LOG-013. */
    private static final BigDecimal MIN_MARGIN = new BigDecimal("0.05");
    private static final BigDecimal MAX_MARGIN = new BigDecimal("0.20");

    private final GrainStockRepository grainStockRepository;
    private final MarginCalculator marginCalculator;

    public InventoryOpportunityReportService(GrainStockRepository grainStockRepository,
                                              MarginCalculator marginCalculator) {
        this.grainStockRepository = grainStockRepository;
        this.marginCalculator = marginCalculator;
    }

    public List<InventoryOpportunityItem> query(InventoryOpportunityFilters filters) {
        return grainStockRepository.findAllWithGrainType(filters.branchId()).stream()
                .map(this::toItem)
                .toList();
    }

    private InventoryOpportunityItem toItem(GrainStockDetail detail) {
        BigDecimal margin = marginCalculator.calculate(
                detail.availableQuantityKg(), detail.referenceStockKg(), MIN_MARGIN, MAX_MARGIN);
        BigDecimal suggestedSalePricePerTon = marginCalculator.suggestedSalePricePerTon(
                detail.purchasePricePerTon(), margin);

        return new InventoryOpportunityItem(detail.grainTypeId(), detail.grainName(),
                detail.availableQuantityKg(), detail.referenceStockKg(), detail.purchasePricePerTon(),
                margin, suggestedSalePricePerTon);
    }
}
