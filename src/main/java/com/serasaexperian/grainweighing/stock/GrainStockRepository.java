package com.serasaexperian.grainweighing.stock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GrainStockRepository extends JpaRepository<GrainStock, UUID> {

    Optional<GrainStock> findByBranchIdAndGrainTypeId(UUID branchId, UUID grainTypeId);

    /** Estoque e Oportunidade de Margem (LOG-015) — join com GrainType para nome/preço/referência. */
    @Query("""
            SELECT new com.serasaexperian.grainweighing.stock.GrainStockDetail(
                s.grainTypeId, g.name, s.availableQuantityKg, g.referenceStockKg, g.purchasePricePerTon)
            FROM GrainStock s JOIN GrainType g ON g.id = s.grainTypeId
            WHERE (:branchId IS NULL OR s.branchId = :branchId)
            ORDER BY g.name
            """)
    List<GrainStockDetail> findAllWithGrainType(@Param("branchId") UUID branchId);
}
