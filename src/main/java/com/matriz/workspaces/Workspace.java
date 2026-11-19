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

    /** Set when the workspace is deleted (soft delete, INC-19, V8); empty while it is active. */
    @Column(name = "deleted_at")
    private java.time.OffsetDateTime deletedAt;

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

    /** updateWorkspace (FR-23); the name already follows BR-01. */
    void rename(String newName) {
        this.name = newName;
    }

    /** deleteWorkspace (FR-24): soft delete; the row and its data are kept (QO). */
    void markDeleted() {
        this.deletedAt = java.time.OffsetDateTime.now();
    }
}
