package com.serasaexperian.grainweighing.ingestion;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Algoritmo puro, sem dependência de Spring/HTTP/banco — testável isoladamente.
 * Pipeline decidido em LOG-007: mediana+MAD (outlier removal) -> range/stdDev/slope
 * sobre amostras limpas -> peso final = média arredondada pela resolução da balança.
 * A confirmação de estabilidade no tempo (COLLECTING -> STABILIZING -> STABLE) é
 * responsabilidade de {@link ScaleSession}, não deste componente.
 */
@Component
public class StabilizationEngine {

    public WeightResult process(List<WeightSample> window, StabilizationProperties config) {
        throw new UnsupportedOperationException(
                "TODO LOG-007: median+MAD outlier removal, then range/stdDev/slope over clean samples, "
                        + "final weight = mean of clean samples rounded to config.scaleResolutionKg()");
    }
}
