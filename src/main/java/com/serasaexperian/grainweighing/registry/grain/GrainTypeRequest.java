package com.serasaexperian.grainweighing.registry.grain;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record GrainTypeRequest(
        @NotBlank String name,
        @NotNull @Positive BigDecimal purchasePricePerTon,
        @NotNull @Positive BigDecimal referenceStockKg) {
}
