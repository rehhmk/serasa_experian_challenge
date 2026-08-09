package com.serasaexperian.grainweighing.stock;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Chamado por weighing.CompleteWeighingUseCase dentro da mesma transação curta
 * (LOG-009) — a entidade retornada por findByBranchIdAndGrainTypeId já está
 * managed pelo persistence context, então increase() é suficiente (dirty checking).
 */
@Service
public class GrainStockService {

    private final GrainStockRepository repository;

    public GrainStockService(GrainStockRepository repository) {
        this.repository = repository;
    }

    public void increaseAvailableQuantity(UUID branchId, UUID grainTypeId, BigDecimal netWeightKg) {
        GrainStock stock = repository.findByBranchIdAndGrainTypeId(branchId, grainTypeId)
                .orElseThrow(() -> new IllegalStateException(
                        "No GrainStock row for branch " + branchId + " and grain type " + grainTypeId));
        stock.increase(netWeightKg);
    }
}
