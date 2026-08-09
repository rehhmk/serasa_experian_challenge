package com.serasaexperian.grainweighing.ingestion;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Estado transitório de uma balança (LOG-005). Uma instância por scaleId, nunca
 * compartilhada — a sincronização é local a esta sessão, nunca um lock global.
 * O ConcurrentHashMap em {@link ScaleSessionManager} só protege o mapa em si.
 *
 * <p>Assume que {@code sample.timestampMs()} é atribuído pelo servidor (não pelo
 * ESP32 — o protocolo externo não fornece device timestamp confiável, CLAUDE.md
 * §13) e chega em ordem cronológica, mesma precondição de {@link StabilizationEngine}.
 *
 * <p>Troca de placa (LOG-016) só invalida a janela em {@code COLLECTING}/
 * {@code STABILIZING}. Uma vez {@code STABLE}/{@code RECORDED}, uma leitura com
 * placa diferente é ignorada para fins de identidade da sessão — só o peso caindo
 * perto de zero por {@code emptyDurationMs} fecha a sessão. Evita reabrir uma
 * {@code TransportTransaction} já concluída por ruído de 1 frame do LPR; defesa em
 * profundidade adicional existe via {@code UNIQUE(transport_transaction_id)}.
 *
 * <p>Se {@code STABLE} for atingido mas o salvamento de negócio (fora desta classe)
 * falhar antes de {@link #markRecorded()} ser chamado, esta sessão não tenta
 * estabilizar de novo — só espera o peso cair para resetar. Ver LOG-018.
 */
public class ScaleSession {

    /**
     * Piso de segurança do tamanho da janela. Sempre usado como
     * {@code Math.max(MAX_WINDOW_SAMPLES, config.minSamples())} — nunca pode ficar
     * abaixo de minSamples, senão nenhuma janela alcançaria o mínimo exigido pelo
     * StabilizationEngine e nenhuma balança jamais estabilizaria, silenciosamente.
     */
    private static final int MAX_WINDOW_SAMPLES = 40;

    private final String scaleId;
    private final Deque<WeightSample> recentReadings = new ArrayDeque<>();

    private String plate;
    private SessionState state = SessionState.COLLECTING;
    private Long stabilizingSinceMs;
    private Long emptySinceMs;
    private long firstReadingAtMs;
    private long lastReadingAtMs;

    public ScaleSession(String scaleId) {
        this.scaleId = scaleId;
    }

    public synchronized WeightResult addReading(String plate, WeightSample sample,
                                                 StabilizationProperties config,
                                                 StabilizationEngine engine) {
        lastReadingAtMs = sample.timestampMs();

        if (state == SessionState.STABLE || state == SessionState.RECORDED) {
            return watchForSessionEnd(sample, config);
        }

        if (this.plate != null && !this.plate.equals(plate)) {
            reset();
        }
        this.plate = plate;

        if (recentReadings.isEmpty()) {
            firstReadingAtMs = sample.timestampMs();
        }
        appendBounded(sample, config);

        WeightResult candidate = engine.process(List.copyOf(recentReadings), config);

        if (!candidate.stable()) {
            state = SessionState.COLLECTING;
            stabilizingSinceMs = null;
            return candidate;
        }

        if (stabilizingSinceMs == null) {
            stabilizingSinceMs = sample.timestampMs();
            state = SessionState.STABILIZING;
            return new WeightResult(false, candidate.weightKg(), candidate.standardDeviation(), candidate.samplesUsed());
        }

        long stableForMs = sample.timestampMs() - stabilizingSinceMs;
        if (stableForMs < config.stabilityDurationMs()) {
            state = SessionState.STABILIZING;
            return new WeightResult(false, candidate.weightKg(), candidate.standardDeviation(), candidate.samplesUsed());
        }

        state = SessionState.STABLE;
        return candidate;
    }

    /**
     * Pós-STABLE/RECORDED: só observa se o peso caiu perto de zero por tempo
     * suficiente para encerrar a sessão (LOG-016, cenário 1). Não roda o algoritmo
     * de estabilização nem verifica troca de placa — ver javadoc da classe.
     */
    private WeightResult watchForSessionEnd(WeightSample sample, StabilizationProperties config) {
        if (sample.weightKg() <= config.emptyThresholdKg()) {
            if (emptySinceMs == null) {
                emptySinceMs = sample.timestampMs();
            } else if (sample.timestampMs() - emptySinceMs >= config.emptyDurationMs()) {
                reset();
            }
        } else {
            emptySinceMs = null;
        }
        return new WeightResult(false, sample.weightKg(), 0, 0);
    }

    private void appendBounded(WeightSample sample, StabilizationProperties config) {
        int bound = Math.max(MAX_WINDOW_SAMPLES, config.minSamples());
        while (recentReadings.size() >= bound) {
            recentReadings.pollFirst();
        }
        recentReadings.addLast(sample);
    }

    public synchronized void markRecorded() {
        this.state = SessionState.RECORDED;
    }

    public synchronized void reset() {
        recentReadings.clear();
        state = SessionState.COLLECTING;
        stabilizingSinceMs = null;
        emptySinceMs = null;
        plate = null;
    }

    public String scaleId() {
        return scaleId;
    }

    public synchronized String plate() {
        return plate;
    }

    public synchronized SessionState state() {
        return state;
    }
}
