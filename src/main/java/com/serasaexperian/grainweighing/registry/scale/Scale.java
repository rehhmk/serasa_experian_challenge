package com.serasaexperian.grainweighing.registry.scale;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * id é a chave natural (mesmo valor do payload do ESP32, ex. "scale-01") — evita
 * uma tabela de lookup extra sem necessidade real (decisão de implementação,
 * ver plano de scaffold).
 */
@Entity
@Table(name = "scales")
public class Scale {

    @Id
    private String id;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "api_key_hash", nullable = false)
    private String apiKeyHash;

    protected Scale() {
    }

    public Scale(String id, UUID branchId, String apiKeyHash) {
        this.id = id;
        this.branchId = branchId;
        this.apiKeyHash = apiKeyHash;
    }

    public String getId() {
        return id;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public String getApiKeyHash() {
        return apiKeyHash;
    }
}
