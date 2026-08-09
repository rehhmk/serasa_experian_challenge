package com.serasaexperian.grainweighing.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.serasaexperian.grainweighing.integration.AbstractIntegrationTest;
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
import com.serasaexperian.grainweighing.registry.branch.Branch;
import com.serasaexperian.grainweighing.registry.branch.BranchRepository;
import com.serasaexperian.grainweighing.registry.grain.GrainType;
import com.serasaexperian.grainweighing.registry.grain.GrainTypeRepository;
import com.serasaexperian.grainweighing.registry.scale.Scale;
import com.serasaexperian.grainweighing.registry.scale.ScaleRepository;
import com.serasaexperian.grainweighing.registry.transaction.TransportTransaction;
import com.serasaexperian.grainweighing.registry.transaction.TransportTransactionRepository;
import com.serasaexperian.grainweighing.registry.transaction.TransportTransactionStatus;
import com.serasaexperian.grainweighing.registry.truck.Truck;
import com.serasaexperian.grainweighing.registry.truck.TruckRepository;
import com.serasaexperian.grainweighing.stock.GrainStock;
import com.serasaexperian.grainweighing.stock.GrainStockRepository;
import com.serasaexperian.grainweighing.weighing.Weighing;
import com.serasaexperian.grainweighing.weighing.WeighingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Valida os 4 relatórios MUST (LOG-015) contra Postgres real via Testcontainers —
 * as queries JPQL fazem join entre entidades sem relação JPA mapeada
 * (Weighing/TransportTransaction/Branch/GrainType), só verificável de ponta a
 * ponta. @Transactional: cada teste roda numa transaction revertida ao final.
 */
@Transactional
class ReportsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BranchRepository branchRepository;
    @Autowired
    private TruckRepository truckRepository;
    @Autowired
    private GrainTypeRepository grainTypeRepository;
    @Autowired
    private ScaleRepository scaleRepository;
    @Autowired
    private GrainStockRepository grainStockRepository;
    @Autowired
    private TransportTransactionRepository transportTransactionRepository;
    @Autowired
    private WeighingRepository weighingRepository;

    @Autowired
    private WeighingBookQueryService weighingBookQueryService;
    @Autowired
    private CostByGrainReportService costByGrainReportService;
    @Autowired
    private InventoryOpportunityReportService inventoryOpportunityReportService;
    @Autowired
    private BranchPerformanceReportService branchPerformanceReportService;

    private Branch branchA;
    private Branch branchB;
    private GrainType soja;
    private GrainType milho;

    @BeforeEach
    void seedFixtures() {
        branchA = branchRepository.save(new Branch(UUID.randomUUID(), "Goiânia", "Goiânia", "GO"));
        branchB = branchRepository.save(new Branch(UUID.randomUUID(), "Sorriso", "Sorriso", "MT"));

        soja = grainTypeRepository.save(new GrainType(UUID.randomUUID(), "Soja " + UUID.randomUUID(),
                new BigDecimal("1000.00"), new BigDecimal("100000.00")));
        milho = grainTypeRepository.save(new GrainType(UUID.randomUUID(), "Milho " + UUID.randomUUID(),
                new BigDecimal("500.00"), new BigDecimal("50000.00")));

        Truck truck1 = truckRepository.save(new Truck(UUID.randomUUID(), "AAA000" + System.nanoTime() % 10,
                new BigDecimal("8000")));
        Truck truck2 = truckRepository.save(new Truck(UUID.randomUUID(), "BBB000" + System.nanoTime() % 10,
                new BigDecimal("9000")));

        Scale scaleA = scaleRepository.save(new Scale("scale-a-" + UUID.randomUUID(), branchA.getId(), "hash-a"));
        Scale scaleB = scaleRepository.save(new Scale("scale-b-" + UUID.randomUUID(), branchB.getId(), "hash-b"));

        grainStockRepository.save(new GrainStock(UUID.randomUUID(), branchA.getId(), soja.getId(),
                new BigDecimal("20000.00")));
        grainStockRepository.save(new GrainStock(UUID.randomUUID(), branchA.getId(), milho.getId(),
                new BigDecimal("50000.00")));
        grainStockRepository.save(new GrainStock(UUID.randomUUID(), branchB.getId(), soja.getId(),
                new BigDecimal("5000.00")));

        Instant now = Instant.now();

        TransportTransaction tx1 = transportTransactionRepository.save(new TransportTransaction(UUID.randomUUID(),
                truck1.getId(), soja.getId(), branchA.getId(), TransportTransactionStatus.COMPLETED,
                new BigDecimal("1000.00"), now.minus(3, ChronoUnit.DAYS), now.minus(2, ChronoUnit.DAYS)));
        TransportTransaction tx2 = transportTransactionRepository.save(new TransportTransaction(UUID.randomUUID(),
                truck2.getId(), soja.getId(), branchB.getId(), TransportTransactionStatus.COMPLETED,
                new BigDecimal("1000.00"), now.minus(3, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS)));
        TransportTransaction tx3 = transportTransactionRepository.save(new TransportTransaction(UUID.randomUUID(),
                truck1.getId(), milho.getId(), branchA.getId(), TransportTransactionStatus.COMPLETED,
                new BigDecimal("500.00"), now.minus(3, ChronoUnit.DAYS), now));

        weighingRepository.save(new Weighing(UUID.randomUUID(), tx1.getId(), scaleA.getId(), truck1.getPlate(),
                new BigDecimal("32000.00"), new BigDecimal("8000"), new BigDecimal("24000.00"), soja.getId(),
                new BigDecimal("24000.00"), now.minus(2, ChronoUnit.DAYS), 20, new BigDecimal("2.5000")));
        weighingRepository.save(new Weighing(UUID.randomUUID(), tx2.getId(), scaleB.getId(), truck2.getPlate(),
                new BigDecimal("20000.00"), new BigDecimal("9000"), new BigDecimal("11000.00"), soja.getId(),
                new BigDecimal("11000.00"), now.minus(1, ChronoUnit.DAYS), 22, new BigDecimal("1.8000")));
        weighingRepository.save(new Weighing(UUID.randomUUID(), tx3.getId(), scaleA.getId(), truck1.getPlate(),
                new BigDecimal("15000.00"), new BigDecimal("8000"), new BigDecimal("7000.00"), milho.getId(),
                new BigDecimal("3500.00"), now, 25, new BigDecimal("3.1000")));
    }

    private ReportPeriod fullPeriod() {
        return new ReportPeriod(Instant.now().minus(4, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.HOURS));
    }

    @Test
    void weighingBookFiltersByBranchAndOrdersByRecordedAtDescending() {
        List<WeighingBookItem> items = weighingBookQueryService.query(fullPeriod(),
                new WeighingBookFilters(branchA.getId(), null, null, null), PageRequest.of(0, 50));

        assertThat(items).hasSize(2);
        assertThat(items.get(0).grainTypeId()).isEqualTo(milho.getId()); // mais recente (now)
        assertThat(items.get(1).grainTypeId()).isEqualTo(soja.getId()); // now-2days
        assertThat(items).allMatch(item -> item.branchId().equals(branchA.getId()));
        assertThat(items).allMatch(item -> item.plate() != null);
    }

    @Test
    void costByGrainAggregatesAcrossBranches() {
        List<CostByGrainItem> items = costByGrainReportService.query(fullPeriod(), new CostByGrainFilters(null));

        CostByGrainItem sojaItem = items.stream().filter(i -> i.grainTypeId().equals(soja.getId())).findFirst()
                .orElseThrow();
        assertThat(sojaItem.loads()).isEqualTo(2);
        assertThat(sojaItem.volumeTons()).isEqualByComparingTo("35.0000");
        assertThat(sojaItem.totalCost()).isEqualByComparingTo("35000.00");
        assertThat(sojaItem.averageCostPerTon()).isEqualByComparingTo("1000.00");

        CostByGrainItem milhoItem = items.stream().filter(i -> i.grainTypeId().equals(milho.getId())).findFirst()
                .orElseThrow();
        assertThat(milhoItem.loads()).isEqualTo(1);
        assertThat(milhoItem.volumeTons()).isEqualByComparingTo("7.0000");
        assertThat(milhoItem.totalCost()).isEqualByComparingTo("3500.00");
    }

    @Test
    void branchPerformanceComputesShareOfTotalVolume() {
        List<BranchPerformanceItem> items = branchPerformanceReportService.query(fullPeriod(),
                new BranchPerformanceFilters(null));

        BranchPerformanceItem a = items.stream().filter(i -> i.branchId().equals(branchA.getId())).findFirst()
                .orElseThrow();
        BranchPerformanceItem b = items.stream().filter(i -> i.branchId().equals(branchB.getId())).findFirst()
                .orElseThrow();

        assertThat(a.loads()).isEqualTo(2);
        assertThat(a.volumeTons()).isEqualByComparingTo("31.0000"); // 24 + 7
        assertThat(b.loads()).isEqualTo(1);
        assertThat(b.volumeTons()).isEqualByComparingTo("11.0000");

        // share = volume da filial / total do periodo (31 + 11 = 42)
        assertThat(a.shareOfTotalVolume().doubleValue()).isCloseTo(31.0 / 42.0, within(0.001));
        assertThat(b.shareOfTotalVolume().doubleValue()).isCloseTo(11.0 / 42.0, within(0.001));
    }

    @Test
    void inventoryOpportunityComputesMarginFromCurrentStock() {
        List<InventoryOpportunityItem> items = inventoryOpportunityReportService.query(
                new InventoryOpportunityFilters(branchA.getId()));

        assertThat(items).hasSize(2); // soja + milho em branchA, branchB excluido pelo filtro

        InventoryOpportunityItem sojaItem = items.stream().filter(i -> i.grainTypeId().equals(soja.getId()))
                .findFirst().orElseThrow();
        // stockRatio = 20000/100000 = 0.2 -> margin = 0.20 - 0.15*0.2 = 0.17
        assertThat(sojaItem.currentMargin()).isEqualByComparingTo("0.1700000000");
        // suggestedPrice = 1000 * 1.17 = 1170
        assertThat(sojaItem.suggestedSalePricePerTon()).isEqualByComparingTo("1170.0000000000");
    }
}
