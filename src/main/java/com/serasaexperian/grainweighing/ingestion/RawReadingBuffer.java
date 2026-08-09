package com.serasaexperian.grainweighing.ingestion;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolve o conflito entre LOG-006 ("raw_readings assíncrono/bufferizado") e
 * CLAUDE.md secao 5/22 (proíbe @Async/worker/BlockingQueue sem aprovação):
 * offer() é O(1), sem I/O, no hot path de cada reading. Quando o buffer atinge
 * flushBatchSize amostras, a própria thread da request que estourou o limiar faz
 * um batch insert síncrono (RawReadingFlusher) — sem nenhuma primitiva nova de
 * concorrência. Custo: 1 em N requests paga a latência do insert; as outras N-1
 * pagam zero. Confirmado como decisão de engenharia (Opção A) antes do scaffold.
 */
@Component
public class RawReadingBuffer {

    private final ConcurrentLinkedQueue<RawReading> queue = new ConcurrentLinkedQueue<>();
    private final int flushBatchSize;
    private final RawReadingFlusher flusher;

    public RawReadingBuffer(@Value("${grainweighing.raw-reading-buffer.flush-batch-size}") int flushBatchSize,
                             RawReadingFlusher flusher) {
        this.flushBatchSize = flushBatchSize;
        this.flusher = flusher;
    }

    public void offer(RawReading reading) {
        queue.offer(reading);
        if (queue.size() >= flushBatchSize) {
            drainAndFlush();
        }
    }

    private void drainAndFlush() {
        List<RawReading> batch = new ArrayList<>(flushBatchSize);
        RawReading next;
        while (batch.size() < flushBatchSize && (next = queue.poll()) != null) {
            batch.add(next);
        }
        if (!batch.isEmpty()) {
            flusher.flush(batch);
        }
    }
}
