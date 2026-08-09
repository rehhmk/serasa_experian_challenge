package com.serasaexperian.grainweighing.registry.transaction;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportTransactionRepository extends JpaRepository<TransportTransaction, UUID> {

    /**
     * Não filtra por branch: um caminhão só deve ter uma transaction OPEN por
     * vez, em qualquer filial (invariante de domínio — não pode estar "em
     * trânsito" em duas filiais simultaneamente). CompleteWeighingUseCase
     * valida separadamente se essa transaction é da mesma filial da balança
     * (LOG-009/011) — distinguir "sem transaction aberta" de "aberta na
     * filial errada" produz mensagens de erro mais específicas. Zero ou mais
     * de uma correspondência deve virar erro de negócio explícito lá, não aqui.
     */
    Optional<TransportTransaction> findByTruckIdAndStatus(UUID truckId, TransportTransactionStatus status);
}
