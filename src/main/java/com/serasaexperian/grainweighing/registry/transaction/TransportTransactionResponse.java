package com.serasaexperian.grainweighing.registry.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransportTransactionResponse(
        UUID id,
        UUID truckId,
        UUID grainTypeId,
        UUID branchId,
        TransportTransactionStatus status,
        BigDecimal purchasePriceSnapshot,
        Instant startedAt,
        Instant finishedAt) {

    static TransportTransactionResponse from(TransportTransaction transaction) {
        return new TransportTransactionResponse(
                transaction.getId(), transaction.getTruckId(), transaction.getGrainTypeId(),
                transaction.getBranchId(), transaction.getStatus(), transaction.getPurchasePriceSnapshot(),
                transaction.getStartedAt(), transaction.getFinishedAt());
    }
}
