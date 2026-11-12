package com.matriz.workspaces;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An organization's workspace (database.md: Workspace). Owned by CMP-02. */
@Entity
@Table(name = "workspace")
class Workspace {

    @Id
    @Column(name = "workspace_id")
    private UUID id;

    private String name;

    protected Workspace() {
    }

    UUID getId() {
        return id;
    }

    String getName() {
        return name;
    }
}
