package com.serasaexperian.grainweighing.registry.grain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrainTypeRepository extends JpaRepository<GrainType, UUID> {
}
