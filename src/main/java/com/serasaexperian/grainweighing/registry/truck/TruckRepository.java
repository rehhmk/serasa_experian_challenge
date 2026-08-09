package com.serasaexperian.grainweighing.registry.truck;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TruckRepository extends JpaRepository<Truck, UUID> {

    Optional<Truck> findByPlate(String plate);
}
