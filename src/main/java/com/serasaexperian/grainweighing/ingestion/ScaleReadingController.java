package com.serasaexperian.grainweighing.ingestion;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint fire-and-forget do ESP32 (protocolo: id, plate, weight — a cada 100ms
 * enquanto houver caminhão presente). ScaleAuthFilter já validou X-Scale-Key antes
 * deste controller ser alcançado (LOG-014). Nunca acessa PostgreSQL diretamente
 * neste caminho (CLAUDE.md secao 4) — Weighing só é persistida via
 * CompleteWeighingUseCase quando a sessão atinge STABLE.
 */
@RestController
public class ScaleReadingController {

    private final ScaleSessionManager sessionManager;
    private final StabilizationEngine stabilizationEngine;
    private final StabilizationProperties stabilizationProperties;
    private final RawReadingBuffer rawReadingBuffer;

    public ScaleReadingController(ScaleSessionManager sessionManager,
                                   StabilizationEngine stabilizationEngine,
                                   StabilizationProperties stabilizationProperties,
                                   RawReadingBuffer rawReadingBuffer) {
        this.sessionManager = sessionManager;
        this.stabilizationEngine = stabilizationEngine;
        this.stabilizationProperties = stabilizationProperties;
        this.rawReadingBuffer = rawReadingBuffer;
    }

    @PostMapping("/api/readings")
    public ResponseEntity<Void> receive(@Valid @RequestBody ScaleReadingRequest request) {
        // TODO LOG-004/007/009:
        // 1. rawReadingBuffer.offer(new RawReading(...)) — auditoria, fora do caminho crítico;
        // 2. sessionManager.sessionFor(request.id()).addReading(request.plate(), sample,
        //    stabilizationProperties, stabilizationEngine);
        // 3. se WeightResult.stable() -> delegar para weighing.CompleteWeighingUseCase (LOG-009),
        //    depois session.markRecorded();
        // 4. sempre retornar 202/204 rápido — a balança é fire-and-forget, não espera negócio.
        throw new UnsupportedOperationException("TODO LOG-004/007/009: see receive() comment");
    }
}
