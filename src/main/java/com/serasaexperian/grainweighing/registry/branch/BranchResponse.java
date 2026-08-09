package com.serasaexperian.grainweighing.registry.branch;

import java.util.UUID;

public record BranchResponse(UUID id, String name, String city, String state) {

    static BranchResponse from(Branch branch) {
        return new BranchResponse(branch.getId(), branch.getName(), branch.getCity(), branch.getState());
    }
}
