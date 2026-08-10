package com.serasaexperian.grainweighing.registry.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.serasaexperian.grainweighing.registry.grain.GrainType;
import com.serasaexperian.grainweighing.registry.grain.GrainTypeRepository;
import com.serasaexperian.grainweighing.shared.ConflictException;
import com.serasaexperian.grainweighing.shared.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * LOG-020: uma TransportTransaction OPEN por truck. O índice único parcial
 * (V10) é a garantia real sob concorrência — coberta por
 * TransportTransactionOpeningIntegrationTest contra Postgres real (inclusive
 * o caso "reabre depois de COMPLETED/CANCELLED", que só faz sentido provar
 * contra o índice de verdade, não um mock). Este teste cobre a lógica de
 * decisão do use case em isolamento.
 */
@ExtendWith(MockitoExtension.class)
class OpenTransportTransactionUseCaseTest {

    private static final UUID TRUCK_ID = UUID.randomUUID();
    private static final UUID GRAIN_TYPE_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final GrainType GRAIN_TYPE =
            new GrainType(GRAIN_TYPE_ID, "Soja", BigDecimal.valueOf(1800), BigDecimal.valueOf(100000));

    @Mock
    private TransportTransactionRepository repository;
    @Mock
    private GrainTypeRepository grainTypeRepository;

    private OpenTransportTransactionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new OpenTransportTransactionUseCase(repository, grainTypeRepository);
    }

    private static TransportTransaction openTransactionFor(UUID truckId) {
        return new TransportTransaction(UUID.randomUUID(), truckId, GRAIN_TYPE_ID, BRANCH_ID,
                TransportTransactionStatus.OPEN, BigDecimal.valueOf(1800), Instant.now(), null);
    }

    @Test
    void firstOpenTransactionForATruckSucceeds() {
        when(grainTypeRepository.findById(GRAIN_TYPE_ID)).thenReturn(Optional.of(GRAIN_TYPE));
        when(repository.findAllByTruckIdAndStatus(TRUCK_ID, TransportTransactionStatus.OPEN))
                .thenReturn(List.of());

        TransportTransaction result = useCase.open(TRUCK_ID, GRAIN_TYPE_ID, BRANCH_ID);

        assertThat(result.getTruckId()).isEqualTo(TRUCK_ID);
        assertThat(result.getStatus()).isEqualTo(TransportTransactionStatus.OPEN);
        assertThat(result.getPurchasePriceSnapshot()).isEqualByComparingTo("1800");
        verify(repository).saveAndFlush(any());
    }

    @Test
    void secondOpenAttemptWhileFirstIsStillOpenIsRejectedWithConflict() {
        when(grainTypeRepository.findById(GRAIN_TYPE_ID)).thenReturn(Optional.of(GRAIN_TYPE));
        when(repository.findAllByTruckIdAndStatus(TRUCK_ID, TransportTransactionStatus.OPEN))
                .thenReturn(List.of(openTransactionFor(TRUCK_ID)));

        assertThatThrownBy(() -> useCase.open(TRUCK_ID, GRAIN_TYPE_ID, BRANCH_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(TRUCK_ID.toString());

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void aTruckAlreadyContaminatedWithManyOpenTransactionsIsStillACleanConflictNotACrash() {
        // Dado real encontrado em produção antes desta correção: um truck do
        // sandbox pode ter dezenas/centenas de OPEN acumuladas (cada
        // dispatch, antes da V10, criava mais uma sem nunca completar
        // nenhuma). findAllByTruckIdAndStatus (List) nunca estoura nesse
        // caso — ao contrário de um findByTruckIdAndStatus (Optional), que
        // lançaria IncorrectResultSizeDataAccessException pra >1 resultado e
        // impediria até a autorrecuperação do sandbox de funcionar.
        when(grainTypeRepository.findById(GRAIN_TYPE_ID)).thenReturn(Optional.of(GRAIN_TYPE));
        List<TransportTransaction> manyOpen = java.util.stream.Stream.generate(() -> openTransactionFor(TRUCK_ID))
                .limit(188)
                .toList();
        when(repository.findAllByTruckIdAndStatus(TRUCK_ID, TransportTransactionStatus.OPEN))
                .thenReturn(manyOpen);

        assertThatThrownBy(() -> useCase.open(TRUCK_ID, GRAIN_TYPE_ID, BRANCH_ID))
                .isInstanceOf(ConflictException.class);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentInsertThatRacesPastTheCheckIsCaughtAsConflictByTheUniqueIndex() {
        // A checagem em memória não viu nenhuma transaction OPEN (outra
        // requisição concorrente ainda não commitou), mas o índice único
        // parcial no banco rejeita o INSERT quando o flush roda — é
        // exatamente essa camada que faz a diferença sob concorrência real.
        when(grainTypeRepository.findById(GRAIN_TYPE_ID)).thenReturn(Optional.of(GRAIN_TYPE));
        when(repository.findAllByTruckIdAndStatus(TRUCK_ID, TransportTransactionStatus.OPEN))
                .thenReturn(List.of());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> useCase.open(TRUCK_ID, GRAIN_TYPE_ID, BRANCH_ID))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void unknownGrainTypeIsRejectedBeforeTouchingTransactionState() {
        UUID unknownGrainTypeId = UUID.randomUUID();
        when(grainTypeRepository.findById(unknownGrainTypeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.open(TRUCK_ID, unknownGrainTypeId, BRANCH_ID))
                .isInstanceOf(NotFoundException.class);

        verify(repository, never()).findAllByTruckIdAndStatus(any(), any());
        verify(repository, never()).saveAndFlush(any());
    }
}
