package com.serasaexperian.grainweighing.registry.grain;

import java.net.URI;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/api/grain-types")
public class GrainTypeController {

    private final GrainTypeRepository repository;

    public GrainTypeController(GrainTypeRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<GrainTypeResponse> create(@Valid @RequestBody GrainTypeRequest request) {
        GrainType grainType = new GrainType(UUID.randomUUID(), request.name(),
                request.purchasePricePerTon(), request.referenceStockKg());
        repository.save(grainType);
        return ResponseEntity.created(URI.create("/api/grain-types/" + grainType.getId()))
                .body(GrainTypeResponse.from(grainType));
    }

    @GetMapping("/{id}")
    public GrainTypeResponse get(@PathVariable UUID id) {
        return repository.findById(id)
                .map(GrainTypeResponse::from)
                .orElseThrow(() -> new NotFoundException("GrainType not found: " + id));
    }

    @GetMapping
    public List<GrainTypeResponse> list() {
        return repository.findAll().stream().map(GrainTypeResponse::from).toList();
    }
}
