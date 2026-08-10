package com.serasaexperian.grainweighing.weighing;

import com.serasaexperian.grainweighing.ingestion.WeightResult;
import com.serasaexperian.grainweighing.registry.scale.Scale;
import com.serasaexperian.grainweighing.registry.scale.ScaleRepository;
import com.serasaexperian.grainweighing.registry.transaction.TransportTransaction;
import com.serasaexperian.grainweighing.registry.transaction.TransportTransactionRepository;
import com.serasaexperian.grainweighing.registry.transaction.TransportTransactionStatus;
import com.serasaexperian.grainweighing.registry.truck.Truck;
import com.serasaexperian.grainweighing.registry.truck.TruckRepository;
import com.serasaexperian.grainweighing.shared.BusinessRuleViolationException;
import com.serasaexperian.grainweighing.shared.NotFoundException;
import com.serasaexperian.grainweighing.stock.GrainStockService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Único ponto de finalização de uma pesagem (LOG-009). Curto e transacional:
 * resolve a TransportTransaction OPEN, calcula net/cost, persiste Weighing,
 * atualiza GrainStock, completa a TransportTransaction.
 * UNIQUE(transport_transaction_id) garante idempotência (LOG-008) contra dupla
 * finalização — o banco deve rejeitar a segunda tentativa, não só o check em
 * memória feito aqui (que existe só para dar uma mensagem de erro clara no
 * caso comum, não como a garantia real).
 */
@Service
public class CompleteWeighingUseCase {

    private static final BigDecimal KG_PER_TON = BigDecimal.valueOf(1000);
    private static final int TON_CONVERSION_SCALE = 6;

    private final WeighingRepository weighingRepository;
    private final ScaleRepository scaleRepository;
    private final TruckRepository truckRepository;
    private final TransportTransactionRepository transportTransactionRepository;
    private final GrainStockService grainStockService;

    public CompleteWeighingUseCase(WeighingRepository weighingRepository,
                                    ScaleRepository scaleRepository,
                                    TruckRepository truckRepository,
                                    TransportTransactionRepository transportTransactionRepository,
                                    GrainStockService grainStockService) {
        this.weighingRepository = weighingRepository;
        this.scaleRepository = scaleRepository;
        this.truckRepository = truckRepository;
        this.transportTransactionRepository = transportTransactionRepository;
        this.grainStockService = grainStockService;
    }

    @Transactional
    public Weighing complete(String scaleId, String plate, WeightResult stableResult) {
        Scale scale = scaleRepository.findById(scaleId)
                .orElseThrow(() -> new NotFoundException("Unknown scale: " + scaleId));
        Truck truck = truckRepository.findByPlate(plate)
                .orElseThrow(() -> new NotFoundException("Unknown truck plate: " + plate));

        TransportTransaction transaction = resolveOpenTransaction(truck.getId(), plate);

        if (!transaction.getBranchId().equals(scale.getBranchId())) {
            throw new BusinessRuleViolationException(
                    "Truck " + plate + " has an OPEN TransportTransaction at a different branch");
        }

        if (weighingRepository.existsByTransportTransactionId(transaction.getId())) {
            throw new BusinessRuleViolationException(
                    "TransportTransaction " + transaction.getId() + " already has a Weighing");
        }

        BigDecimal grossWeightKg = BigDecimal.valueOf(stableResult.weightKg()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tareWeightKg = truck.getTareWeightKg();

        if (grossWeightKg.compareTo(tareWeightKg) <= 0) {
            throw new BusinessRuleViolationException(
                    "Gross weight " + grossWeightKg + "kg must be greater than tare weight " + tareWeightKg
                            + "kg for truck " + plate);
        }

        BigDecimal netWeightKg = grossWeightKg.subtract(tareWeightKg);
        BigDecimal netWeightTon = netWeightKg.divide(KG_PER_TON, TON_CONVERSION_SCALE, RoundingMode.HALF_UP);
        BigDecimal cost = transaction.getPurchasePriceSnapshot().multiply(netWeightTon)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal standardDeviation = BigDecimal.valueOf(stableResult.standardDeviation())
                .setScale(4, RoundingMode.HALF_UP);

        Weighing weighing = new Weighing(UUID.randomUUID(), transaction.getId(), scaleId, plate,
                grossWeightKg, tareWeightKg, netWeightKg, transaction.getGrainTypeId(), cost,
                Instant.now(), stableResult.samplesUsed(), standardDeviation);
        weighingRepository.save(weighing);

        grainStockService.increaseAvailableQuantity(scale.getBranchId(), transaction.getGrainTypeId(), netWeightKg);
        transaction.complete();

        return weighing;
    }

    private TransportTransaction resolveOpenTransaction(UUID truckId, String plate) {
        try {
            return transportTransactionRepository.findByTruckIdAndStatus(truckId, TransportTransactionStatus.OPEN)
                    .orElseThrow(() -> new BusinessRuleViolationException(
                            "No OPEN TransportTransaction for truck " + plate));
        } catch (IncorrectResultSizeDataAccessException e) {
            throw new BusinessRuleViolationException(
                    "Multiple OPEN TransportTransactions for truck " + plate + " — ambiguous, cannot auto-resolve");
        }
    }
}
