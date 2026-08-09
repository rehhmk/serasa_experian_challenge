package com.serasaexperian.grainweighing.registry.truck;

import java.math.BigDecimal;
import java.util.UUID;

public record TruckResponse(UUID id, String plate, BigDecimal tareWeightKg) {

    static TruckResponse from(Truck truck) {
        return new TruckResponse(truck.getId(), truck.getPlate(), truck.getTareWeightKg());
    }
}
