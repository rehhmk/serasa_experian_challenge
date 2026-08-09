package com.serasaexperian.grainweighing.ingestion;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Algoritmo puro, sem dependência de Spring/HTTP/banco — testável isoladamente.
 * Pipeline decidido em LOG-007: mediana+MAD (outlier removal) -> range/stdDev/slope
 * sobre amostras limpas -> peso final = média arredondada pela resolução da balança.
 * A confirmação de estabilidade no tempo (COLLECTING -> STABILIZING -> STABLE) é
 * responsabilidade de {@link ScaleSession}, não deste componente: o {@code stable}
 * devolvido aqui significa "esta janela específica satisfaz os critérios
 * instantâneos", não "confirmado por tempo suficiente".
 *
 * <p>Assume que {@code window} chega em ordem cronológica de chegada (verdade hoje,
 * já que {@link ScaleSession#recentReadings} é alimentado nessa ordem pelo ESP32) —
 * necessário para o cálculo de slope. Não valida essa ordem em runtime: o custo de
 * validar toda a janela a cada leitura (a cada ~100ms) não se justifica para uma
 * precondição de contrato interno.
 */
@Component
public class StabilizationEngine {

    public WeightResult process(List<WeightSample> window, StabilizationProperties config) {
        if (window == null || window.isEmpty()) {
            throw new IllegalArgumentException("window must not be empty");
        }
        int n = window.size();

        if (n < config.minSamples()) {
            double rawMean = mean(window);
            return new WeightResult(false,
                    roundToResolution(rawMean, config.scaleResolutionKg()),
                    populationStdDev(window, rawMean), n);
        }

        double median = median(sortedWeights(window));
        double mad = median(sortedAbsoluteDeviations(window, median));
        double robustSigma = 1.4826 * mad;
        double threshold = Math.max(3 * robustSigma, config.outlierToleranceKg());

        List<WeightSample> clean = window.stream()
                .filter(s -> Math.abs(s.weightKg() - median) <= threshold)
                .toList();

        double validRatio = clean.size() / (double) n;
        if (clean.isEmpty() || validRatio < config.minValidRatio()) {
            List<WeightSample> basis = clean.isEmpty() ? window : clean;
            double basisMean = mean(basis);
            return new WeightResult(false,
                    roundToResolution(basisMean, config.scaleResolutionKg()),
                    populationStdDev(basis, basisMean), clean.size());
        }

        double cleanMean = mean(clean);
        double range = max(clean) - min(clean);
        double stdDev = populationStdDev(clean, cleanMean);
        double slope = slope(clean);

        boolean stable = stdDev <= config.maxStdDevKg()
                && range <= config.maxRangeKg()
                && Math.abs(slope) <= config.maxSlopeKgPerSec();

        double finalWeight = roundToResolution(cleanMean, config.scaleResolutionKg());
        return new WeightResult(stable, finalWeight, stdDev, clean.size());
    }

    private static double[] sortedWeights(List<WeightSample> samples) {
        return samples.stream().mapToDouble(WeightSample::weightKg).sorted().toArray();
    }

    private static double[] sortedAbsoluteDeviations(List<WeightSample> samples, double median) {
        return samples.stream().mapToDouble(s -> Math.abs(s.weightKg() - median)).sorted().toArray();
    }

    private static double median(double[] sortedValues) {
        int m = sortedValues.length;
        int mid = m / 2;
        return (m % 2 == 1) ? sortedValues[mid] : (sortedValues[mid - 1] + sortedValues[mid]) / 2.0;
    }

    private static double mean(List<WeightSample> samples) {
        return samples.stream().mapToDouble(WeightSample::weightKg).average().orElseThrow();
    }

    private static double populationStdDev(List<WeightSample> samples, double mean) {
        double sumSquares = samples.stream()
                .mapToDouble(s -> Math.pow(s.weightKg() - mean, 2))
                .sum();
        return Math.sqrt(sumSquares / samples.size());
    }

    private static double max(List<WeightSample> samples) {
        return samples.stream().mapToDouble(WeightSample::weightKg).max().orElseThrow();
    }

    private static double min(List<WeightSample> samples) {
        return samples.stream().mapToDouble(WeightSample::weightKg).min().orElseThrow();
    }

    private static double slope(List<WeightSample> chronologicallyOrdered) {
        if (chronologicallyOrdered.size() < 2) {
            return 0.0;
        }
        WeightSample first = chronologicallyOrdered.get(0);
        WeightSample last = chronologicallyOrdered.get(chronologicallyOrdered.size() - 1);
        double dtSeconds = (last.timestampMs() - first.timestampMs()) / 1000.0;
        if (dtSeconds <= 0) {
            return 0.0;
        }
        return (last.weightKg() - first.weightKg()) / dtSeconds;
    }

    private static double roundToResolution(double value, double resolutionKg) {
        return Math.round(value / resolutionKg) * resolutionKg;
    }
}
