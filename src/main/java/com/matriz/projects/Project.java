package com.matriz.projects;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A project (database.md: Project). Owned by CMP-04. Only the attributes other modules need so far are mapped;
 * the project features themselves come in a later increment.
 */
@Entity
@Table(name = "project")
class Project {

    @Id
    @Column(name = "project_id")
    private UUID id;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    protected Project() {
    }

    UUID getWorkspaceId() {
        return workspaceId;
    }
}
