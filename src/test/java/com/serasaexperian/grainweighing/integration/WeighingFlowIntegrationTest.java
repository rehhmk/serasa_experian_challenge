package com.serasaexperian.grainweighing.integration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Fluxo completo de LOG-012: OPEN TransportTransaction -> sequência de readings
 * -> STABLE -> exactly one Weighing -> TransportTransaction COMPLETED ->
 * net/cost/stock corretos. @Disabled até o hot path (ingestion.
 * ScaleReadingController/ScaleSession/StabilizationEngine) e
 * weighing.CompleteWeighingUseCase estarem implementados.
 */
@Disabled("TODO: pending ingestion + weighing implementation")
class WeighingFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void openTransactionThenStableReadingsProducesExactlyOneCompletedWeighing() {
        // 1. POST /api/branches, /api/trucks, /api/grain-types, /api/scales (setup cadastro)
        // 2. POST /api/transport-transactions (status OPEN)
        // 3. POST /api/readings repetido com X-Scale-Key valido ate estabilizar
        // 4. GET /api/reports/weighings -> exatamente 1 Weighing para essa transaction
        // 5. GET /api/transport-transactions/{id} -> status COMPLETED
        // 6. net = gross - tare; cost = purchasePriceSnapshot * net/1000; GrainStock atualizado
    }
}
