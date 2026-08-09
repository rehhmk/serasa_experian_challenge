package com.serasaexperian.grainweighing.weighing;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeighingRepository extends JpaRepository<Weighing, UUID> {

    boolean existsByTransportTransactionId(UUID transportTransactionId);
}
