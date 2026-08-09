package com.serasaexperian.grainweighing.registry.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Transação de compra e pesagem de um tipo de grão para um caminhão, início e
 * fim (enunciado do desafio). purchasePriceSnapshot congela o preço de compra
 * do GrainType no momento da abertura, para que uma mudança de preço posterior
 * não altere retroativamente o custo de uma transação já em andamento.
 */
@Entity
@Table(name = "transport_transactions")
public class TransportTransaction {

    @Id
    private UUID id;

    @Column(name = "truck_id", nullable = false)
    private UUID truckId;

    @Column(name = "grain_type_id", nullable = false)
    private UUID grainTypeId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransportTransactionStatus status;

    @Column(name = "purchase_price_snapshot", nullable = false)
    private BigDecimal purchasePriceSnapshot;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected TransportTransaction() {
    }

    public TransportTransaction(UUID id, UUID truckId, UUID grainTypeId, UUID branchId,
                                 TransportTransactionStatus status, BigDecimal purchasePriceSnapshot,
                                 Instant startedAt, Instant finishedAt) {
        this.id = id;
        this.truckId = truckId;
        this.grainTypeId = grainTypeId;
        this.branchId = branchId;
        this.status = status;
        this.purchasePriceSnapshot = purchasePriceSnapshot;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public void cancel() {
        this.status = TransportTransactionStatus.CANCELLED;
        this.finishedAt = Instant.now();
    }

    public void complete() {
        this.status = TransportTransactionStatus.COMPLETED;
        this.finishedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTruckId() {
        return truckId;
    }

    public UUID getGrainTypeId() {
        return grainTypeId;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public TransportTransactionStatus getStatus() {
        return status;
    }

    public BigDecimal getPurchasePriceSnapshot() {
        return purchasePriceSnapshot;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
