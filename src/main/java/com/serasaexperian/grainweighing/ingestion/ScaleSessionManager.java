package com.serasaexperian.grainweighing.ingestion;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * scaleId -> ScaleSession (LOG-005). Atividade da balança A nunca bloqueia a
 * balança B: o ConcurrentHashMap protege só a estrutura do mapa; a sincronização
 * de cada sessão é responsabilidade da própria {@link ScaleSession}.
 */
@Component
public class ScaleSessionManager {

    private final ConcurrentHashMap<String, ScaleSession> sessions = new ConcurrentHashMap<>();

    public ScaleSession sessionFor(String scaleId) {
        return sessions.computeIfAbsent(scaleId, ScaleSession::new);
    }

    public void reset(String scaleId) {
        ScaleSession session = sessions.get(scaleId);
        if (session != null) {
            session.reset();
        }
    }
}
