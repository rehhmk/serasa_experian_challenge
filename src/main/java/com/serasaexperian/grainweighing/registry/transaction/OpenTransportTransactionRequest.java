package com.serasaexperian.grainweighing.registry.transaction;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public record OpenTransportTransactionRequest(
        @NotNull UUID truckId,
        @NotNull UUID grainTypeId,
        @NotNull UUID branchId) {
}
