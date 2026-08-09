package com.serasaexperian.grainweighing.ingestion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ScaleReadingRequest(
        @NotBlank String id,
        @NotBlank String plate,
        @Positive double weight) {
}
