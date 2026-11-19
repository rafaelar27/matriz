package com.matriz.workspaces;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An organization's workspace (database.md: Workspace). Owned by CMP-02. */
@Entity
@Table(name = "workspace")
class Workspace {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "workspace_id")
    private UUID id;

    private String name;

    protected Workspace() {
    }

    Workspace(String name) {
        this.name = name;
    }

    UUID getId() {
        return id;
    }

    String getName() {
        return name;
    }
}
