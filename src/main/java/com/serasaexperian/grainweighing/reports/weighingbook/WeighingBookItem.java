package com.serasaexperian.grainweighing.reports.weighingbook;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Único item de relatório com plate — Livro de Pesagens, acesso autorizado (LOG-015). */
public record WeighingBookItem(
        UUID id,
        Instant recordedAt,
        UUID branchId,
        String scaleId,
        String plate,
        UUID grainTypeId,
        BigDecimal grossWeightKg,
        BigDecimal tareWeightKg,
        BigDecimal netWeightKg,
        BigDecimal cost) {
}
