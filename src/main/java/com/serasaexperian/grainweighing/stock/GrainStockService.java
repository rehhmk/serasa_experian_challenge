package com.serasaexperian.grainweighing.stock;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Chamado por weighing.CompleteWeighingUseCase dentro da mesma transação curta
 * (LOG-009). O incremento é um UPDATE atômico no banco (ver
 * GrainStockRepository.increaseAvailableQuantity), não read-modify-write via
 * entidade gerenciada — duas balanças finalizando pesagens do mesmo
 * branch/grainType ao mesmo tempo não perdem incremento uma da outra.
 */
@Service
public class GrainStockService {

    private final GrainStockRepository repository;

    public GrainStockService(GrainStockRepository repository) {
        this.repository = repository;
    }

    public void increaseAvailableQuantity(UUID branchId, UUID grainTypeId, BigDecimal netWeightKg) {
        int updated = repository.increaseAvailableQuantity(branchId, grainTypeId, netWeightKg);
        if (updated == 0) {
            throw new IllegalStateException(
                    "No GrainStock row for branch " + branchId + " and grain type " + grainTypeId);
        }
    }
}
