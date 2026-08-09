package com.serasaexperian.grainweighing.registry.branch;

import jakarta.validation.constraints.NotBlank;

public record BranchRequest(@NotBlank String name, String city, String state) {
}
