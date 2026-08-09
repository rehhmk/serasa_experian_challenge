package com.serasaexperian.grainweighing.ingestion;

public record WeightResult(
        boolean stable,
        double weightKg,
        double standardDeviation,
        int samplesUsed) {
}
