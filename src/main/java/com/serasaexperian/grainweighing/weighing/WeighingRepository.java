package com.serasaexperian.grainweighing.weighing;

import com.serasaexperian.grainweighing.reports.weighingbook.WeighingBookItem;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeighingRepository extends JpaRepository<Weighing, UUID> {

    boolean existsByTransportTransactionId(UUID transportTransactionId);

    /**
     * Livro de Pesagens (LOG-015) — único relatório que expõe plate. branchId vem
     * de TransportTransaction (Weighing não guarda branchId diretamente).
     */
    @Query("""
            SELECT new com.serasaexperian.grainweighing.reports.weighingbook.WeighingBookItem(
                w.id, w.recordedAt, t.branchId, w.scaleId, w.plate, w.grainTypeId,
                w.grossWeightKg, w.tareWeightKg, w.netWeightKg, w.cost)
            FROM Weighing w JOIN TransportTransaction t ON t.id = w.transportTransactionId
            WHERE w.recordedAt >= :from AND w.recordedAt <= :to
              AND (:branchId IS NULL OR t.branchId = :branchId)
              AND (:grainTypeId IS NULL OR w.grainTypeId = :grainTypeId)
              AND (:scaleId IS NULL OR w.scaleId = :scaleId)
              AND (:plate IS NULL OR w.plate = :plate)
            ORDER BY w.recordedAt DESC
            """)
    Page<WeighingBookItem> search(@Param("from") Instant from, @Param("to") Instant to,
                                   @Param("branchId") UUID branchId, @Param("grainTypeId") UUID grainTypeId,
                                   @Param("scaleId") String scaleId, @Param("plate") String plate,
                                   Pageable pageable);

    @Query("""
            SELECT new com.serasaexperian.grainweighing.weighing.GrainTypeCostAggregate(
                w.grainTypeId, g.name, COUNT(w), SUM(w.netWeightKg), SUM(w.cost))
            FROM Weighing w
            JOIN TransportTransaction t ON t.id = w.transportTransactionId
            JOIN GrainType g ON g.id = w.grainTypeId
            WHERE w.recordedAt >= :from AND w.recordedAt <= :to
              AND (:branchId IS NULL OR t.branchId = :branchId)
            GROUP BY w.grainTypeId, g.name
            """)
    List<GrainTypeCostAggregate> aggregateByGrainType(@Param("from") Instant from, @Param("to") Instant to,
                                                        @Param("branchId") UUID branchId);

    @Query("""
            SELECT new com.serasaexperian.grainweighing.weighing.BranchCostAggregate(
                t.branchId, b.name, COUNT(w), SUM(w.netWeightKg), SUM(w.cost))
            FROM Weighing w
            JOIN TransportTransaction t ON t.id = w.transportTransactionId
            JOIN Branch b ON b.id = t.branchId
            WHERE w.recordedAt >= :from AND w.recordedAt <= :to
              AND (:grainTypeId IS NULL OR w.grainTypeId = :grainTypeId)
            GROUP BY t.branchId, b.name
            """)
    List<BranchCostAggregate> aggregateByBranch(@Param("from") Instant from, @Param("to") Instant to,
                                                 @Param("grainTypeId") UUID grainTypeId);
}
