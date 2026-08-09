package com.serasaexperian.grainweighing.ingestion;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RawReadingFlusher {

    private final RawReadingRepository repository;

    public RawReadingFlusher(RawReadingRepository repository) {
        this.repository = repository;
    }

    public void flush(List<RawReading> batch) {
        repository.saveAll(batch);
    }
}
