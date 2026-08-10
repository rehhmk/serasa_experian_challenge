package com.serasaexperian.grainweighing.stock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GrainStockRepository extends JpaRepository<GrainStock, UUID> {

    Optional<GrainStock> findByBranchIdAndGrainTypeId(UUID branchId, UUID grainTypeId);

    /**
     * UPDATE atômico no banco (SET quantity = quantity + delta) em vez de
     * read-modify-write via entidade gerenciada — evita lost update quando duas
     * balanças finalizam pesagens do mesmo branch/grainType concorrentemente
     * (cada transação abre e comita curto, sem lock explícito; o incremento
     * relativo no SQL é o que garante a soma, não a ordem de commit).
     * Retorna o número de linhas afetadas (0 = sem GrainStock para o par).
     */
    @Modifying
    @Query("""
            UPDATE GrainStock s
            SET s.availableQuantityKg = s.availableQuantityKg + :deltaKg
            WHERE s.branchId = :branchId AND s.grainTypeId = :grainTypeId
            """)
    int increaseAvailableQuantity(@Param("branchId") UUID branchId,
                                   @Param("grainTypeId") UUID grainTypeId,
                                   @Param("deltaKg") BigDecimal deltaKg);

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
