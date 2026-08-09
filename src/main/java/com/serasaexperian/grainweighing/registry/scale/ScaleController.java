package com.serasaexperian.grainweighing.registry.scale;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scales")
public class ScaleController {

    private final ScaleService service;

    public ScaleController(ScaleService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScaleCreatedResponse create(@Valid @RequestBody ScaleRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<ScaleSummary> list() {
        return service.list().stream()
                .map(scale -> new ScaleSummary(scale.getId(), scale.getBranchId()))
                .toList();
    }

    // apiKeyHash nunca sai daqui — nem em relatórios, nem em listagens (LOG-015).
    public record ScaleSummary(String id, UUID branchId) {
    }
}
