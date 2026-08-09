package com.serasaexperian.grainweighing.reports.weighingbook;

import java.util.UUID;

public record WeighingBookFilters(UUID branchId, UUID grainTypeId, String scaleId, String plate) {
}
