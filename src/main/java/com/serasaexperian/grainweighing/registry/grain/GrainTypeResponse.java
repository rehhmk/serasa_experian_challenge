package com.serasaexperian.grainweighing.registry.grain;

import java.math.BigDecimal;
import java.util.UUID;

public record GrainTypeResponse(UUID id, String name, BigDecimal purchasePricePerTon, BigDecimal referenceStockKg) {

    static GrainTypeResponse from(GrainType grainType) {
        return new GrainTypeResponse(grainType.getId(), grainType.getName(),
                grainType.getPurchasePricePerTon(), grainType.getReferenceStockKg());
    }
}
