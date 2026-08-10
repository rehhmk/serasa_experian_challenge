package com.serasaexperian.grainweighing.registry.transaction;

import com.serasaexperian.grainweighing.registry.grain.GrainType;
import com.serasaexperian.grainweighing.registry.grain.GrainTypeRepository;
import com.serasaexperian.grainweighing.shared.ConflictException;
import com.serasaexperian.grainweighing.shared.NotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Único ponto de abertura de uma TransportTransaction (LOG-020). Um truck só
 * pode ter uma transaction OPEN por vez — abrir uma segunda enquanto a
 * primeira segue OPEN é o cenário que deixava CompleteWeighingUseCase sem
 * conseguir decidir qual finalizar (múltiplas correspondências ambíguas),
 * fazendo a pesagem se perder silenciosamente.
 *
 * Duas camadas, não uma: o SELECT abaixo rejeita rápido no caminho comum
 * (sem round-trip extra de exceção), mas quem garante a invariante sob
 * concorrência real é o índice único parcial no banco (V10) — um
 * "exists then save" sozinho tem janela de corrida entre as duas operações.
 */
@Service
public class OpenTransportTransactionUseCase {

    private final TransportTransactionRepository repository;
    private final GrainTypeRepository grainTypeRepository;

    public OpenTransportTransactionUseCase(TransportTransactionRepository repository,
                                            GrainTypeRepository grainTypeRepository) {
        this.repository = repository;
        this.grainTypeRepository = grainTypeRepository;
    }

    @Transactional
    public TransportTransaction open(UUID truckId, UUID grainTypeId, UUID branchId) {
        GrainType grainType = grainTypeRepository.findById(grainTypeId)
                .orElseThrow(() -> new NotFoundException("GrainType not found: " + grainTypeId));

        // findAllByTruckIdAndStatus, não findByTruckIdAndStatus: um truck já
        // contaminado por dados anteriores à V10 (mais de uma OPEN) não pode
        // fazer essa checagem estourar IncorrectResultSizeDataAccessException
        // — precisa continuar virando um 409 limpo, senão nem a
        // autorrecuperação do sandbox consegue se recuperar dele.
        if (!repository.findAllByTruckIdAndStatus(truckId, TransportTransactionStatus.OPEN).isEmpty()) {
            throw conflict(truckId);
        }

        TransportTransaction transaction = new TransportTransaction(UUID.randomUUID(), truckId, grainTypeId,
                branchId, TransportTransactionStatus.OPEN, grainType.getPurchasePricePerTon(), Instant.now(), null);
        try {
            // saveAndFlush, não save: força o INSERT a rodar aqui dentro do
            // try, em vez de só no commit da transação — é o que faz uma
            // violação do índice único parcial (corrida real) virar
            // DataIntegrityViolationException neste método, capturável,
            // em vez de estourar depois como falha genérica de commit.
            repository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException e) {
            throw conflict(truckId);
        }
        return transaction;
    }

    private static ConflictException conflict(UUID truckId) {
        return new ConflictException("Truck " + truckId + " already has an OPEN TransportTransaction");
    }
}
