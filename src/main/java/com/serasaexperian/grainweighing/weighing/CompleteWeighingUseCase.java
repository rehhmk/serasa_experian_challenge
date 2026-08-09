package com.serasaexperian.grainweighing.weighing;

import com.serasaexperian.grainweighing.ingestion.WeightResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Único ponto de finalização de uma pesagem (LOG-009). Curto e transacional:
 * resolve a TransportTransaction OPEN, calcula net/cost, persiste Weighing,
 * atualiza GrainStock, completa a TransportTransaction.
 * UNIQUE(transport_transaction_id) garante idempotência (LOG-008) contra dupla
 * finalização — o banco deve rejeitar a segunda tentativa, não só um check em memória.
 */
@Service
public class CompleteWeighingUseCase {

    private final WeighingRepository weighingRepository;

    public CompleteWeighingUseCase(WeighingRepository weighingRepository) {
        this.weighingRepository = weighingRepository;
    }

    @Transactional
    public Weighing complete(String scaleId, String plate, WeightResult stableResult) {
        // TODO LOG-008/009/011 (assumption sinalizada no plano, a validar com teste):
        // 1. resolver a única TransportTransaction OPEN para
        //    (truck.plate == plate, branchId == scale.branchId); zero ou mais de uma
        //    correspondência deve lançar erro de negócio explícito, não 500 genérico;
        // 2. validar scale.branchId == transaction.branchId antes de prosseguir;
        // 3. obter tare do Truck; netWeight = grossWeight - tare;
        // 4. cost = transaction.purchasePriceSnapshot * (netWeight / 1000), em BigDecimal;
        // 5. persistir Weighing (transportTransactionId é UNIQUE);
        // 6. GrainStockService.increaseAvailableQuantity(...);
        // 7. marcar TransportTransaction como COMPLETED.
        throw new UnsupportedOperationException("TODO LOG-008/009/011: see complete() comment");
    }
}
