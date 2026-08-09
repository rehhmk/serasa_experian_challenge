package com.serasaexperian.grainweighing.reports.inventory;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryOpportunityItem(
        UUID grainTypeId,
        String grainName,
        BigDecimal availableQuantityKg,
        BigDecimal referenceStockKg,
        BigDecimal purchasePricePerTon,
        BigDecimal currentMargin,
        BigDecimal suggestedSalePricePerTon) {
}
