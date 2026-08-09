package com.serasaexperian.grainweighing.ingestion;

import com.serasaexperian.grainweighing.weighing.CompleteWeighingUseCase;
import com.serasaexperian.grainweighing.weighing.Weighing;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ScaleReadingController.class);

    private final ScaleSessionManager sessionManager;
    private final StabilizationEngine stabilizationEngine;
    private final StabilizationProperties stabilizationProperties;
    private final RawReadingBuffer rawReadingBuffer;
    private final CompleteWeighingUseCase completeWeighingUseCase;

    public ScaleReadingController(ScaleSessionManager sessionManager,
                                   StabilizationEngine stabilizationEngine,
                                   StabilizationProperties stabilizationProperties,
                                   RawReadingBuffer rawReadingBuffer,
                                   CompleteWeighingUseCase completeWeighingUseCase) {
        this.sessionManager = sessionManager;
        this.stabilizationEngine = stabilizationEngine;
        this.stabilizationProperties = stabilizationProperties;
        this.rawReadingBuffer = rawReadingBuffer;
        this.completeWeighingUseCase = completeWeighingUseCase;
    }

    @PostMapping("/api/readings")
    public ResponseEntity<Void> receive(@Valid @RequestBody ScaleReadingRequest request) {
        long nowMs = System.currentTimeMillis();

        rawReadingBuffer.offer(new RawReading(request.id(), request.plate(),
                BigDecimal.valueOf(request.weight()), Instant.ofEpochMilli(nowMs)));

        ScaleSession session = sessionManager.sessionFor(request.id());
        WeightResult result = session.addReading(request.plate(),
                new WeightSample(nowMs, request.weight()), stabilizationProperties, stabilizationEngine);

        if (result.stable()) {
            completeWeighing(request.id(), request.plate(), session, result);
        }

        return ResponseEntity.accepted().build();
    }

    /**
     * Se a finalização falhar (ex: sem TransportTransaction OPEN correspondente),
     * não há retry automático nesta versão (LOG-018) — a sessão fica em STABLE até
     * o peso cair perto de zero e resetar. A balança é fire-and-forget e não
     * processa o corpo/status da resposta, então sempre retornamos 202 rápido;
     * o erro fica registrado no log para investigação, não no response.
     */
    private void completeWeighing(String scaleId, String plate, ScaleSession session, WeightResult result) {
        try {
            Weighing weighing = completeWeighingUseCase.complete(scaleId, plate, result);
            session.markRecorded();
            log.info("weighing completed: transactionId={} scaleId={} plate={} netWeightKg={}",
                    weighing.getTransportTransactionId(), scaleId, plate, weighing.getNetWeightKg());
        } catch (RuntimeException e) {
            log.error("weighing completion failed: scaleId={} plate={}", scaleId, plate, e);
        }
    }
}
