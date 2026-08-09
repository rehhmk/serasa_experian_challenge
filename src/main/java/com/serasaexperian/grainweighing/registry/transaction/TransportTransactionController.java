package com.serasaexperian.grainweighing.registry.transaction;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.serasaexperian.grainweighing.registry.grain.GrainType;
import com.serasaexperian.grainweighing.registry.grain.GrainTypeRepository;
import com.serasaexperian.grainweighing.shared.BusinessRuleViolationException;
import com.serasaexperian.grainweighing.shared.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transport-transactions")
public class TransportTransactionController {

    private final TransportTransactionRepository repository;
    private final GrainTypeRepository grainTypeRepository;

    public TransportTransactionController(TransportTransactionRepository repository,
                                           GrainTypeRepository grainTypeRepository) {
        this.repository = repository;
        this.grainTypeRepository = grainTypeRepository;
    }

    @PostMapping
    public ResponseEntity<TransportTransactionResponse> open(
            @Valid @RequestBody OpenTransportTransactionRequest request) {
        GrainType grainType = grainTypeRepository.findById(request.grainTypeId())
                .orElseThrow(() -> new NotFoundException("GrainType not found: " + request.grainTypeId()));
        TransportTransaction transaction = new TransportTransaction(
                UUID.randomUUID(), request.truckId(), request.grainTypeId(), request.branchId(),
                TransportTransactionStatus.OPEN, grainType.getPurchasePricePerTon(), Instant.now(), null);
        repository.save(transaction);
        return ResponseEntity.created(URI.create("/api/transport-transactions/" + transaction.getId()))
                .body(TransportTransactionResponse.from(transaction));
    }

    @PostMapping("/{id}/cancel")
    public TransportTransactionResponse cancel(@PathVariable UUID id) {
        TransportTransaction transaction = find(id);
        if (transaction.getStatus() != TransportTransactionStatus.OPEN) {
            throw new BusinessRuleViolationException("Only OPEN transactions can be cancelled: " + id);
        }
        transaction.cancel();
        return TransportTransactionResponse.from(transaction);
    }

    @GetMapping("/{id}")
    public TransportTransactionResponse get(@PathVariable UUID id) {
        return TransportTransactionResponse.from(find(id));
    }

    @GetMapping
    public List<TransportTransactionResponse> list() {
        return repository.findAll().stream().map(TransportTransactionResponse::from).toList();
    }

    private TransportTransaction find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("TransportTransaction not found: " + id));
    }
}
