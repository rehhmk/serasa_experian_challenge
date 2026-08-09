package com.serasaexperian.grainweighing.reports;

import java.time.Instant;
import java.util.UUID;
import com.serasaexperian.grainweighing.reports.branchperformance.BranchPerformanceFilters;
import com.serasaexperian.grainweighing.reports.branchperformance.BranchPerformanceItem;
import com.serasaexperian.grainweighing.reports.branchperformance.BranchPerformanceReportService;
import com.serasaexperian.grainweighing.reports.costbygrain.CostByGrainFilters;
import com.serasaexperian.grainweighing.reports.costbygrain.CostByGrainItem;
import com.serasaexperian.grainweighing.reports.costbygrain.CostByGrainReportService;
import com.serasaexperian.grainweighing.reports.inventory.InventoryOpportunityFilters;
import com.serasaexperian.grainweighing.reports.inventory.InventoryOpportunityItem;
import com.serasaexperian.grainweighing.reports.inventory.InventoryOpportunityReportService;
import com.serasaexperian.grainweighing.reports.weighingbook.WeighingBookFilters;
import com.serasaexperian.grainweighing.reports.weighingbook.WeighingBookItem;
import com.serasaexperian.grainweighing.reports.weighingbook.WeighingBookQueryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 4 relatórios MUST fechados no LOG-015. Contrato uniforme:
 * { period, filters, data }. plate só aparece em /weighings (Livro de Pesagens) —
 * os demais são agregados e nunca a expõem (LGPD, ver LOG-015).
 */
@RestController
@RequestMapping("/api/reports")
public class ReportsController {

    private final WeighingBookQueryService weighingBookQueryService;
    private final CostByGrainReportService costByGrainReportService;
    private final InventoryOpportunityReportService inventoryOpportunityReportService;
    private final BranchPerformanceReportService branchPerformanceReportService;

    public ReportsController(WeighingBookQueryService weighingBookQueryService,
                              CostByGrainReportService costByGrainReportService,
                              InventoryOpportunityReportService inventoryOpportunityReportService,
                              BranchPerformanceReportService branchPerformanceReportService) {
        this.weighingBookQueryService = weighingBookQueryService;
        this.costByGrainReportService = costByGrainReportService;
        this.inventoryOpportunityReportService = inventoryOpportunityReportService;
        this.branchPerformanceReportService = branchPerformanceReportService;
    }

    @GetMapping("/weighings")
    public ReportEnvelope<WeighingBookItem> weighings(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID grainTypeId,
            @RequestParam(required = false) String scaleId,
            @RequestParam(required = false) String plate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        ReportPeriod period = new ReportPeriod(from, to);
        WeighingBookFilters filters = new WeighingBookFilters(branchId, grainTypeId, scaleId, plate);
        Pageable pageable = PageRequest.of(page, size);
        return new ReportEnvelope<>(period, filters, weighingBookQueryService.query(period, filters, pageable));
    }

    @GetMapping("/cost-by-grain")
    public ReportEnvelope<CostByGrainItem> costByGrain(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) UUID branchId) {
        ReportPeriod period = new ReportPeriod(from, to);
        CostByGrainFilters filters = new CostByGrainFilters(branchId);
        return new ReportEnvelope<>(period, filters, costByGrainReportService.query(period, filters));
    }

    @GetMapping("/inventory-opportunities")
    public ReportEnvelope<InventoryOpportunityItem> inventoryOpportunities(
            @RequestParam(required = false) UUID branchId) {
        InventoryOpportunityFilters filters = new InventoryOpportunityFilters(branchId);
        return new ReportEnvelope<>(null, filters, inventoryOpportunityReportService.query(filters));
    }

    @GetMapping("/branches/performance")
    public ReportEnvelope<BranchPerformanceItem> branchesPerformance(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) UUID grainTypeId) {
        ReportPeriod period = new ReportPeriod(from, to);
        BranchPerformanceFilters filters = new BranchPerformanceFilters(grainTypeId);
        return new ReportEnvelope<>(period, filters, branchPerformanceReportService.query(period, filters));
    }
}
