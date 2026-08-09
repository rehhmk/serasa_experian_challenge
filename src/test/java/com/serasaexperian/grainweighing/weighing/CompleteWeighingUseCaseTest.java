package com.serasaexperian.grainweighing.weighing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.serasaexperian.grainweighing.ingestion.WeightResult;
import com.serasaexperian.grainweighing.registry.scale.Scale;
import com.serasaexperian.grainweighing.registry.scale.ScaleRepository;
import com.serasaexperian.grainweighing.registry.transaction.TransportTransaction;
import com.serasaexperian.grainweighing.registry.transaction.TransportTransactionRepository;
import com.serasaexperian.grainweighing.registry.transaction.TransportTransactionStatus;
import com.serasaexperian.grainweighing.registry.truck.Truck;
import com.serasaexperian.grainweighing.registry.truck.TruckRepository;
import com.serasaexperian.grainweighing.shared.BusinessRuleViolationException;
import com.serasaexperian.grainweighing.stock.GrainStockService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

@ExtendWith(MockitoExtension.class)
class CompleteWeighingUseCaseTest {

    private static final String SCALE_ID = "scale-01";
    private static final String PLATE = "ABC1D23";
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID TRUCK_ID = UUID.randomUUID();
    private static final UUID GRAIN_TYPE_ID = UUID.randomUUID();

    @Mock
    private WeighingRepository weighingRepository;
    @Mock
    private ScaleRepository scaleRepository;
    @Mock
    private TruckRepository truckRepository;
    @Mock
    private TransportTransactionRepository transportTransactionRepository;
    @Mock
    private GrainStockService grainStockService;

    private CompleteWeighingUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CompleteWeighingUseCase(
                weighingRepository, scaleRepository, truckRepository, transportTransactionRepository, grainStockService);
    }

    private Scale scaleAtBranch(UUID branchId) {
        return new Scale(SCALE_ID, branchId, "hash");
    }

    private Truck truckWithTare(BigDecimal tareWeightKg) {
        return new Truck(TRUCK_ID, PLATE, tareWeightKg);
    }

    private TransportTransaction openTransactionAt(UUID branchId, BigDecimal purchasePriceSnapshot) {
        return new TransportTransaction(UUID.randomUUID(), TRUCK_ID, GRAIN_TYPE_ID, branchId,
                TransportTransactionStatus.OPEN, purchasePriceSnapshot, Instant.now(), null);
    }

    @Test
    void secondFinalizationOfSameTransactionIsRejected() {
        Scale scale = scaleAtBranch(BRANCH_ID);
        Truck truck = truckWithTare(BigDecimal.valueOf(8000));
        TransportTransaction transaction = openTransactionAt(BRANCH_ID, BigDecimal.valueOf(1000));

        when(scaleRepository.findById(SCALE_ID)).thenReturn(Optional.of(scale));
        when(truckRepository.findByPlate(PLATE)).thenReturn(Optional.of(truck));
        when(transportTransactionRepository.findByTruckIdAndStatus(TRUCK_ID, TransportTransactionStatus.OPEN))
                .thenReturn(Optional.of(transaction));
        when(weighingRepository.existsByTransportTransactionId(transaction.getId())).thenReturn(true);

        WeightResult stable = new WeightResult(true, 32000.0, 3.0, 20);

        assertThatThrownBy(() -> useCase.complete(SCALE_ID, PLATE, stable))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(weighingRepository, never()).save(any());
    }

    @Test
    void netWeightIsGrossMinusTare() {
        Scale scale = scaleAtBranch(BRANCH_ID);
        Truck truck = truckWithTare(BigDecimal.valueOf(8000));
        TransportTransaction transaction = openTransactionAt(BRANCH_ID, BigDecimal.valueOf(1000));

        when(scaleRepository.findById(SCALE_ID)).thenReturn(Optional.of(scale));
        when(truckRepository.findByPlate(PLATE)).thenReturn(Optional.of(truck));
        when(transportTransactionRepository.findByTruckIdAndStatus(TRUCK_ID, TransportTransactionStatus.OPEN))
                .thenReturn(Optional.of(transaction));
        when(weighingRepository.existsByTransportTransactionId(transaction.getId())).thenReturn(false);

        WeightResult stable = new WeightResult(true, 32000.0, 3.0, 20);

        Weighing result = useCase.complete(SCALE_ID, PLATE, stable);

        assertThat(result.getGrossWeightKg()).isEqualByComparingTo("32000.00");
        assertThat(result.getTareWeightKg()).isEqualByComparingTo("8000");
        assertThat(result.getNetWeightKg()).isEqualByComparingTo("24000.00");

        ArgumentCaptor<Weighing> captor = ArgumentCaptor.forClass(Weighing.class);
        verify(weighingRepository).save(captor.capture());
        assertThat(captor.getValue().getNetWeightKg()).isEqualByComparingTo("24000.00");
    }

    @Test
    void costUsesPurchasePriceSnapshotNotCurrentGrainTypePrice() {
        Scale scale = scaleAtBranch(BRANCH_ID);
        Truck truck = truckWithTare(BigDecimal.valueOf(8000));
        // snapshot congelado em 1000/ton no momento da abertura, mesmo que o preço
        // atual do GrainType tenha mudado depois — este use case nunca consulta o
        // preço atual, só o snapshot da transaction.
        TransportTransaction transaction = openTransactionAt(BRANCH_ID, BigDecimal.valueOf(1000));

        when(scaleRepository.findById(SCALE_ID)).thenReturn(Optional.of(scale));
        when(truckRepository.findByPlate(PLATE)).thenReturn(Optional.of(truck));
        when(transportTransactionRepository.findByTruckIdAndStatus(TRUCK_ID, TransportTransactionStatus.OPEN))
                .thenReturn(Optional.of(transaction));
        when(weighingRepository.existsByTransportTransactionId(transaction.getId())).thenReturn(false);

        WeightResult stable = new WeightResult(true, 10000.0, 3.0, 20); // net = 2000kg = 2 ton

        Weighing result = useCase.complete(SCALE_ID, PLATE, stable);

        assertThat(result.getCost()).isEqualByComparingTo("2000.00"); // 2 ton * 1000/ton
    }

    @Test
    void ambiguousOpenTransactionMatchThrowsBusinessRuleViolation() {
        Scale scale = scaleAtBranch(BRANCH_ID);
        Truck truck = truckWithTare(BigDecimal.valueOf(8000));

        when(scaleRepository.findById(SCALE_ID)).thenReturn(Optional.of(scale));
        when(truckRepository.findByPlate(PLATE)).thenReturn(Optional.of(truck));
        when(transportTransactionRepository.findByTruckIdAndStatus(TRUCK_ID, TransportTransactionStatus.OPEN))
                .thenThrow(new IncorrectResultSizeDataAccessException(1, 2));

        WeightResult stable = new WeightResult(true, 32000.0, 3.0, 20);

        assertThatThrownBy(() -> useCase.complete(SCALE_ID, PLATE, stable))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(weighingRepository, never()).save(any());
    }

    @Test
    void mismatchedScaleBranchAndTransactionBranchIsRejected() {
        UUID scaleBranch = UUID.randomUUID();
        UUID transactionBranch = UUID.randomUUID();
        Scale scale = scaleAtBranch(scaleBranch);
        Truck truck = truckWithTare(BigDecimal.valueOf(8000));
        TransportTransaction transaction = openTransactionAt(transactionBranch, BigDecimal.valueOf(1000));

        when(scaleRepository.findById(SCALE_ID)).thenReturn(Optional.of(scale));
        when(truckRepository.findByPlate(PLATE)).thenReturn(Optional.of(truck));
        when(transportTransactionRepository.findByTruckIdAndStatus(TRUCK_ID, TransportTransactionStatus.OPEN))
                .thenReturn(Optional.of(transaction));

        WeightResult stable = new WeightResult(true, 32000.0, 3.0, 20);

        assertThatThrownBy(() -> useCase.complete(SCALE_ID, PLATE, stable))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(weighingRepository, never()).save(any());
    }
}
