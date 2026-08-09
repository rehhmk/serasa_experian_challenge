package com.serasaexperian.grainweighing.weighing;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompleteWeighingUseCaseTest {

    @Test
    @Disabled("TODO LOG-008/009: implementar CompleteWeighingUseCase.complete()")
    void secondFinalizationOfSameTransactionIsRejected() {
        // UNIQUE(transport_transaction_id) deve impedir duas Weighing para a mesma transaction
    }

    @Test
    @Disabled("TODO LOG-011: implementar CompleteWeighingUseCase.complete()")
    void netWeightIsGrossMinusTare() {
    }

    @Test
    @Disabled("TODO LOG-011: implementar CompleteWeighingUseCase.complete()")
    void costUsesPurchasePriceSnapshotNotCurrentGrainTypePrice() {
    }

    @Test
    @Disabled("TODO: assumption a validar - ver secao 'Assumptions sinalizadas' do plano de scaffold")
    void ambiguousOpenTransactionMatchThrowsBusinessRuleViolation() {
        // zero ou mais de uma TransportTransaction OPEN para scaleId+plate -> erro explicito, nao 500
    }

    @Test
    @Disabled("TODO: assumption a validar - ver secao 'Assumptions sinalizadas' do plano de scaffold")
    void mismatchedScaleBranchAndTransactionBranchIsRejected() {
        // scale.branchId != transaction.branchId -> erro de negocio, nao deve finalizar silenciosamente
    }
}
