package com.serasaexperian.grainweighing.registry.branch;

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
@RequestMapping("/api/branches")
public class BranchController {

    private final BranchRepository repository;

    public BranchController(BranchRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<BranchResponse> create(@Valid @RequestBody BranchRequest request) {
        Branch branch = new Branch(UUID.randomUUID(), request.name(), request.city(), request.state());
        repository.save(branch);
        return ResponseEntity.created(URI.create("/api/branches/" + branch.getId()))
                .body(BranchResponse.from(branch));
    }

    @GetMapping("/{id}")
    public BranchResponse get(@PathVariable UUID id) {
        return repository.findById(id)
                .map(BranchResponse::from)
                .orElseThrow(() -> new NotFoundException("Branch not found: " + id));
    }

    @GetMapping
    public List<BranchResponse> list() {
        return repository.findAll().stream().map(BranchResponse::from).toList();
    }
}
