package com.serasaexperian.grainweighing.registry.transaction;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import com.serasaexperian.grainweighing.shared.BusinessRuleViolationException;
import com.serasaexperian.grainweighing.shared.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transport-transactions")
public class TransportTransactionController {

    private final TransportTransactionRepository repository;
    private final OpenTransportTransactionUseCase openTransportTransactionUseCase;

    public TransportTransactionController(TransportTransactionRepository repository,
                                           OpenTransportTransactionUseCase openTransportTransactionUseCase) {
        this.repository = repository;
        this.openTransportTransactionUseCase = openTransportTransactionUseCase;
    }

    @PostMapping
    public ResponseEntity<TransportTransactionResponse> open(
            @Valid @RequestBody OpenTransportTransactionRequest request) {
        TransportTransaction transaction = openTransportTransactionUseCase.open(
                request.truckId(), request.grainTypeId(), request.branchId());
        return ResponseEntity.created(URI.create("/api/transport-transactions/" + transaction.getId()))
                .body(TransportTransactionResponse.from(transaction));
    }

    /**
     * @Transactional aqui não é opcional: sem ele, o find() abaixo devolve
     * uma entidade já "detached" (a mini-transação read-only do repository
     * já fechou) — mutar transaction.cancel() nesse estado só muda o objeto
     * Java em memória, nunca chega a fazer flush no banco. O endpoint
     * respondia 200 com "status": "CANCELLED" no corpo sem nunca ter escrito
     * nada — bug pré-existente, descoberto ao investigar por que o
     * saneamento manual do LOG-020 não reduzia a contagem real de linhas.
     */
    @PostMapping("/{id}/cancel")
    @Transactional
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

    /**
     * truckId+status juntos filtram para "as transactions OPEN deste truck"
     * (uso real: sandbox resolvendo um conflito de abertura, LOG-020) — sem
     * endpoint dedicado novo. Qualquer combinação parcial (só um dos dois)
     * não é um caso de uso existente hoje, então cai no comportamento
     * default de listar tudo, em vez de inventar semântica pra ele agora.
     *
     * findAllByTruckIdAndStatus (List), não findByTruckIdAndStatus
     * (Optional): este endpoint é literalmente a ferramenta que o sandbox
     * usa pra descobrir e limpar um truck já contaminado com mais de uma
     * OPEN (dado anterior à V10) — teria que funcionar exatamente no caso
     * em que "mais de uma" é verdade, não estourar por causa disso.
     */
    @GetMapping
    public List<TransportTransactionResponse> list(@RequestParam(required = false) UUID truckId,
                                                     @RequestParam(required = false) TransportTransactionStatus status) {
        List<TransportTransaction> transactions = truckId != null && status != null
                ? repository.findAllByTruckIdAndStatus(truckId, status)
                : repository.findAll();
        return transactions.stream().map(TransportTransactionResponse::from).toList();
    }

    private TransportTransaction find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("TransportTransaction not found: " + id));
    }
}
