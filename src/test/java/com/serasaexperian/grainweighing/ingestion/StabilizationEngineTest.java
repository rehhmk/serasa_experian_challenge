package com.serasaexperian.grainweighing.ingestion;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Cenários definidos em LOG-007 / LOG-012 (10 testes que valem a pena escrever).
 * @Disabled até StabilizationEngine.process() ser implementado — os nomes e
 * comentários já fixam o contrato esperado para quem for implementar.
 */
class StabilizationEngineTest {

    @Test
    @Disabled("TODO LOG-007: implementar StabilizationEngine.process()")
    void fullyStableWindowIsStable() {
        // janela com variacao minima -> stable=true, weightKg = media arredondada pela resolucao
    }

    @Test
    @Disabled("TODO LOG-007: implementar StabilizationEngine.process()")
    void stableWithSmallNoiseIsStable() {
        // ruido pequeno dentro de MAX_STD_DEV/MAX_RANGE -> stable=true
    }

    @Test
    @Disabled("TODO LOG-007: implementar StabilizationEngine.process()")
    void singleLargeOutlierIsRemovedByMedianMad() {
        // uma leitura muito fora do resto -> removida pelo outlier removal, nao derruba a estabilidade
    }

    @Test
    @Disabled("TODO LOG-007: implementar StabilizationEngine.process()")
    void clearUpwardTrendIsNotStable() {
        // caminhao ainda entrando na balanca -> slope acima de MAX_SLOPE -> stable=false
    }

    @Test
    @Disabled("TODO LOG-007: implementar StabilizationEngine.process()")
    void clearDownwardTrendIsNotStable() {
        // caminhao saindo da balanca -> slope abaixo de -MAX_SLOPE -> stable=false
    }

    @Test
    @Disabled("TODO LOG-007: implementar StabilizationEngine.process()")
    void oscillationBeyondThresholdIsNotStable() {
        // range/stdDev acima do limite mesmo apos outlier removal -> stable=false
    }

    @Test
    @Disabled("TODO LOG-007: implementar StabilizationEngine.process()")
    void insufficientSamplesIsNotStable() {
        // menos amostras que MIN_SAMPLES -> stable=false, independente da dispersao
    }

    @Test
    @Disabled("TODO LOG-007/LOG-016: implementar maquina de estados em ScaleSession")
    void stableForLessThanStabilityDurationDoesNotSave() {
        // criterios passam mas por menos tempo que STABILITY_DURATION -> nao salva ainda
    }

    @Test
    @Disabled("TODO LOG-007/LOG-016: implementar maquina de estados em ScaleSession")
    void stableForFullStabilityDurationSaves() {
        // criterios mantidos pelo tempo minimo -> STABLE, salva
    }

    @Test
    @Disabled("TODO LOG-007: implementar StabilizationEngine.process()")
    void finalWeightIsRoundedToScaleResolution() {
        // media das amostras limpas arredondada para o multiplo mais proximo de scaleResolutionKg
    }
}
