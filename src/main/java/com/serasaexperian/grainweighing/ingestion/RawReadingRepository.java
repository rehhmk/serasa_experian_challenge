package com.serasaexperian.grainweighing.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RawReadingRepository extends JpaRepository<RawReading, Long> {
}
