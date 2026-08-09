package com.serasaexperian.grainweighing.registry.scale;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.serasaexperian.grainweighing.ingestion.ScaleAuthFilter;
import com.serasaexperian.grainweighing.ingestion.ScaleCredentialsCache;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

/**
 * Gera apiKey (alta entropia, token aleatório — não senha, por isso SHA-256 e não
 * BCrypt, ver LOG-014), persiste só o hash e mantém o ScaleCredentialsCache
 * atualizado para que o ScaleAuthFilter nunca precise consultar o banco por
 * reading. loadCredentialsIntoCache roda uma vez no startup, depois que o Flyway
 * já aplicou as migrations (dependência gerenciada pela auto-configuração do
 * Spring Boot antes do EntityManagerFactory ficar disponível).
 */
@Service
public class ScaleService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ScaleRepository repository;
    private final ScaleCredentialsCache credentialsCache;

    public ScaleService(ScaleRepository repository, ScaleCredentialsCache credentialsCache) {
        this.repository = repository;
        this.credentialsCache = credentialsCache;
    }

    @PostConstruct
    void loadCredentialsIntoCache() {
        Map<String, String> hashesByScaleId = repository.findAll().stream()
                .collect(Collectors.toMap(Scale::getId, Scale::getApiKeyHash));
        credentialsCache.loadAll(hashesByScaleId);
    }

    public ScaleCreatedResponse create(ScaleRequest request) {
        String apiKey = generateApiKey();
        String apiKeyHash = ScaleAuthFilter.sha256Hex(apiKey);
        Scale scale = new Scale(request.id(), request.branchId(), apiKeyHash);
        repository.save(scale);
        credentialsCache.put(scale.getId(), apiKeyHash);
        return new ScaleCreatedResponse(scale.getId(), scale.getBranchId(), apiKey);
    }

    public List<Scale> list() {
        return repository.findAll();
    }

    private String generateApiKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
