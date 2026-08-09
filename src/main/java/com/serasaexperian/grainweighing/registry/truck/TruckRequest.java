package com.serasaexperian.grainweighing.registry.truck;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record TruckRequest(
        @NotBlank String plate,
        @NotNull @PositiveOrZero BigDecimal tareWeightKg) {
}
