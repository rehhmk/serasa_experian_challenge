package com.serasaexperian.grainweighing.ingestion;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Estado transitório de uma balança (LOG-005). Uma instância por scaleId, nunca
 * compartilhada — a sincronização é local a esta sessão, nunca um lock global.
 * O ConcurrentHashMap em {@link ScaleSessionManager} só protege o mapa em si.
 */
public class ScaleSession {

    private final String scaleId;
    private final Deque<WeightSample> recentReadings = new ArrayDeque<>();

    private String plate;
    private SessionState state = SessionState.COLLECTING;
    private Long stabilizingSinceMs;
    private long firstReadingAtMs;
    private long lastReadingAtMs;

    public ScaleSession(String scaleId) {
        this.scaleId = scaleId;
    }

    /**
     * Adiciona uma leitura e reavalia o estado da sessão.
     * TODO LOG-007/LOG-016:
     * - troca de plate mid-window (plate != null && !plate.equals(this.plate)) descarta a janela e reinicia;
     * - janela limitada (bounded, sem crescer indefinidamente — LOG-004/007);
     * - roda StabilizationEngine.process() sobre a janela;
     * - máquina de estados COLLECTING -> STABILIZING -> STABLE (stabilityDurationMs contínuo);
     * - peso perto de zero por emptyDurationMs após STABLE/RECORDED -> reset() (fim de sessão).
     */
    public synchronized WeightResult addReading(String plate, WeightSample sample,
                                                 StabilizationProperties config,
                                                 StabilizationEngine engine) {
        throw new UnsupportedOperationException("TODO LOG-007/LOG-016: see addReading javadoc");
    }

    public synchronized void markRecorded() {
        this.state = SessionState.RECORDED;
    }

    public synchronized void reset() {
        recentReadings.clear();
        state = SessionState.COLLECTING;
        stabilizingSinceMs = null;
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
