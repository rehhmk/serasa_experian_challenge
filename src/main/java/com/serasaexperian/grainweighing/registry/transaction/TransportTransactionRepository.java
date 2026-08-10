package com.serasaexperian.grainweighing.registry.transaction;

import java.util.List;
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
     * filial errada" produz mensagens de erro mais específicas.
     *
     * Retorna Optional (não List) de propósito: CompleteWeighingUseCase quer
     * exatamente essa semântica — zero é "sem transaction", exatamente uma é
     * o caminho feliz, mais de uma é ambiguidade real que deve estourar
     * IncorrectResultSizeDataAccessException (capturada lá, LOG-009) em vez
     * de escolher uma arbitrariamente. Quem só precisa saber "existe alguma
     * OPEN?" ou "quais são elas?" — OpenTransportTransactionUseCase,
     * TransportTransactionController.list — usa findAllByTruckIdAndStatus
     * abaixo, que nunca estoura nessa situação (LOG-020: um truck já
     * contaminado por dados anteriores à migration V10 não pode travar em
     * 500 ao tentar se recuperar sozinho).
     */
    Optional<TransportTransaction> findByTruckIdAndStatus(UUID truckId, TransportTransactionStatus status);

    List<TransportTransaction> findAllByTruckIdAndStatus(UUID truckId, TransportTransactionStatus status);
}
