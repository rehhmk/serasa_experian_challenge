package com.serasaexperian.grainweighing.registry.branch;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "branches")
public class Branch {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String city;

    @Column(length = 2)
    private String state;

    protected Branch() {
    }

    public Branch(UUID id, String name, String city, String state) {
        this.id = id;
        this.name = name;
        this.city = city;
        this.state = state;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }
}
