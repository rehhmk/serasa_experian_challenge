package com.serasaexperian.grainweighing.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Cenários definidos em LOG-007 / LOG-012 (10 testes que valem a pena escrever).
 * Os 8 testes do algoritmo puro (StabilizationEngine) estão implementados abaixo.
 * Os 2 testes de máquina de estados (ScaleSession) continuam @Disabled — próxima PR.
 */
class StabilizationEngineTest {

    private static final StabilizationProperties CONFIG = new StabilizationProperties(
            20,     // minSamples
            0.8,    // minValidRatio
            30.0,   // maxStdDevKg
            100.0,  // maxRangeKg
            10.0,   // maxSlopeKgPerSec
            3000L,  // stabilityDurationMs (não usado pelo Engine)
            20.0,   // scaleResolutionKg
            200.0,  // emptyThresholdKg (não usado pelo Engine)
            1000L,  // emptyDurationMs (não usado pelo Engine)
            20.0);  // outlierToleranceKg

    private final StabilizationEngine engine = new StabilizationEngine();

    private static List<WeightSample> windowOf(double... weights) {
        List<WeightSample> samples = new ArrayList<>();
        for (int i = 0; i < weights.length; i++) {
            samples.add(new WeightSample(i * 100L, weights[i]));
        }
        return samples;
    }

    @Test
    void fullyStableWindowIsStable() {
        double[] weights = new double[20];
        Arrays.fill(weights, 5000.0);

        WeightResult result = engine.process(windowOf(weights), CONFIG);

        assertThat(result.stable()).isTrue();
        assertThat(result.weightKg()).isEqualTo(5000.0, within(0.001));
        assertThat(result.standardDeviation()).isEqualTo(0.0, within(0.001));
        assertThat(result.samplesUsed()).isEqualTo(20);
    }

    @Test
    void stableWithSmallNoiseIsStable() {
        double[] weights = new double[20];
        for (int i = 0; i < 20; i++) {
            weights[i] = 5000 + (i % 2 == 0 ? 3 : -3);
        }

        WeightResult result = engine.process(windowOf(weights), CONFIG);

        assertThat(result.stable()).isTrue();
        assertThat(result.weightKg()).isEqualTo(5000.0, within(0.001));
        assertThat(result.standardDeviation()).isEqualTo(3.0, within(0.001));
        assertThat(result.samplesUsed()).isEqualTo(20);
    }

    @Test
    void singleLargeOutlierIsRemovedByMedianMad() {
        double[] weights = new double[20];
        Arrays.fill(weights, 5000.0);
        weights[10] = 5800.0;

        WeightResult result = engine.process(windowOf(weights), CONFIG);

        assertThat(result.stable()).isTrue();
        assertThat(result.weightKg()).isEqualTo(5000.0, within(0.001));
        assertThat(result.standardDeviation()).isEqualTo(0.0, within(0.001));
        assertThat(result.samplesUsed()).isEqualTo(19);
    }

    @Test
    void clearUpwardTrendIsNotStable() {
        double[] weights = new double[20];
        for (int i = 0; i < 20; i++) {
            weights[i] = 5000 + 1.5 * i;
        }

        WeightResult result = engine.process(windowOf(weights), CONFIG);

        assertThat(result.stable()).isFalse();
        assertThat(result.samplesUsed()).isEqualTo(20);
        assertThat(result.weightKg()).isEqualTo(5020.0, within(0.001));
    }

    @Test
    void clearDownwardTrendIsNotStable() {
        double[] weights = new double[20];
        for (int i = 0; i < 20; i++) {
            weights[i] = 6000 - 1.5 * i;
        }

        WeightResult result = engine.process(windowOf(weights), CONFIG);

        assertThat(result.stable()).isFalse();
        assertThat(result.samplesUsed()).isEqualTo(20);
        assertThat(result.weightKg()).isEqualTo(5980.0, within(0.001));
    }

    @Test
    void oscillationBeyondThresholdIsNotStable() {
        double[] weights = new double[20];
        weights[0] = 5000.0;
        for (int i = 1; i < 19; i++) {
            weights[i] = (i % 2 == 1) ? 5150.0 : 4850.0;
        }
        weights[19] = 5000.0;

        WeightResult result = engine.process(windowOf(weights), CONFIG);

        assertThat(result.stable()).isFalse();
        assertThat(result.samplesUsed()).isEqualTo(20);
        assertThat(result.standardDeviation()).isEqualTo(142.3025, within(0.01));
    }

    @Test
    void insufficientSamplesIsNotStable() {
        double[] weights = new double[19];
        Arrays.fill(weights, 5000.0);

        WeightResult result = engine.process(windowOf(weights), CONFIG);

        assertThat(result.stable()).isFalse();
        assertThat(result.samplesUsed()).isEqualTo(19);
        assertThat(result.standardDeviation()).isEqualTo(0.0, within(0.001));
    }

    @Test
    void finalWeightIsRoundedToScaleResolution() {
        double[] weights = new double[20];
        Arrays.fill(weights, 5011.0);

        WeightResult result = engine.process(windowOf(weights), CONFIG);

        assertThat(result.stable()).isTrue();
        assertThat(result.weightKg()).isEqualTo(5020.0, within(0.001));
        assertThat(result.samplesUsed()).isEqualTo(20);
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
}
