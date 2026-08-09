package com.serasaexperian.grainweighing.stock;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrainStockRepository extends JpaRepository<GrainStock, UUID> {

    Optional<GrainStock> findByBranchIdAndGrainTypeId(UUID branchId, UUID grainTypeId);
}
