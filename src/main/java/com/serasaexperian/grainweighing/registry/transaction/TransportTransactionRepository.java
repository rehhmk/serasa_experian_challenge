package com.serasaexperian.grainweighing.registry.transaction;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportTransactionRepository extends JpaRepository<TransportTransaction, UUID> {

    /**
     * Suporte à assumption de CompleteWeighingUseCase (ver plano de scaffold):
     * única transaction OPEN por (truck, branch da balança) — zero ou mais de
     * uma correspondência deve virar erro de negócio explícito lá, não aqui.
     */
    Optional<TransportTransaction> findByTruckIdAndBranchIdAndStatus(
            UUID truckId, UUID branchId, TransportTransactionStatus status);
}
