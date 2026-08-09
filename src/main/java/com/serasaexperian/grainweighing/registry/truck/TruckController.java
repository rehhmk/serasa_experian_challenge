package com.serasaexperian.grainweighing.registry.truck;

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
@RequestMapping("/api/trucks")
public class TruckController {

    private final TruckRepository repository;

    public TruckController(TruckRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<TruckResponse> create(@Valid @RequestBody TruckRequest request) {
        Truck truck = new Truck(UUID.randomUUID(), request.plate(), request.tareWeightKg());
        repository.save(truck);
        return ResponseEntity.created(URI.create("/api/trucks/" + truck.getId()))
                .body(TruckResponse.from(truck));
    }

    @GetMapping("/{id}")
    public TruckResponse get(@PathVariable UUID id) {
        return repository.findById(id)
                .map(TruckResponse::from)
                .orElseThrow(() -> new NotFoundException("Truck not found: " + id));
    }

    @GetMapping
    public List<TruckResponse> list() {
        return repository.findAll().stream().map(TruckResponse::from).toList();
    }
}
