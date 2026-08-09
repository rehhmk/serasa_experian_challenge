package com.serasaexperian.grainweighing.registry.scale;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ScaleRequest(@NotBlank String id, @NotNull UUID branchId) {
}
