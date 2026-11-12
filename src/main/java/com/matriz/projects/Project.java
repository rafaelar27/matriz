package com.matriz.projects;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A project (database.md: Project). Owned by CMP-04. Optional fields per DA-03; archive flag per DA-11. */
@Entity
@Table(name = "project")
class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "project_id")
    private UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "owner_member_id")
    private UUID ownerMemberId;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "is_archived", nullable = false)
    private boolean archived;

    protected Project() {
    }

    Project(UUID workspaceId) {
        this.workspaceId = workspaceId;
        this.archived = false;
    }

    UUID getId() {
        return id;
    }

    UUID getWorkspaceId() {
        return workspaceId;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    String getDescription() {
        return description;
    }

    void setDescription(String description) {
        this.description = description;
    }

    UUID getOwnerMemberId() {
        return ownerMemberId;
    }

    void setOwnerMemberId(UUID ownerMemberId) {
        this.ownerMemberId = ownerMemberId;
    }

    LocalDate getStartDate() {
        return startDate;
    }

    void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    LocalDate getDueDate() {
        return dueDate;
    }

    void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    boolean isArchived() {
        return archived;
    }
}
