package com.serasaexperian.grainweighing.ingestion;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * scaleId -> apiKeyHash em memória (mesmo padrão do ScaleSessionManager). Existe
 * para que o {@link ScaleAuthFilter} nunca precise consultar o banco por reading —
 * isso violaria o hot path (CLAUDE.md secao 4) em 100% das leituras, pior que o
 * caso do raw_readings. Populado no startup e atualizado quando uma balança é
 * criada/rotacionada em registry.scale.ScaleService.
 */
@Component
public class ScaleCredentialsCache {

    private final Map<String, String> apiKeyHashByScaleId = new ConcurrentHashMap<>();

    public void put(String scaleId, String apiKeyHash) {
        apiKeyHashByScaleId.put(scaleId, apiKeyHash);
    }

    public void loadAll(Map<String, String> apiKeyHashesByScaleId) {
        apiKeyHashByScaleId.putAll(apiKeyHashesByScaleId);
    }

    public void evict(String scaleId) {
        apiKeyHashByScaleId.remove(scaleId);
    }

    public boolean matches(String scaleId, String apiKeyHash) {
        return apiKeyHash != null && apiKeyHash.equals(apiKeyHashByScaleId.get(scaleId));
    }
}
