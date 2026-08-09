package com.serasaexperian.grainweighing.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Assumptions calibráveis do algoritmo de estabilização — ver LOG-007 e LOG-016.
 * Não são tolerâncias regulatórias ou verdades físicas.
 */
@ConfigurationProperties(prefix = "grainweighing.stabilization")
public record StabilizationProperties(
        int minSamples,
        double minValidRatio,
        double maxStdDevKg,
        double maxRangeKg,
        double maxSlopeKgPerSec,
        long stabilityDurationMs,
        double scaleResolutionKg,
        double emptyThresholdKg,
        long emptyDurationMs) {
}
