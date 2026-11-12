package com.matriz.tasks;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A task (database.md: Task). Owned by CMP-05. */
@Entity
@Table(name = "task")
class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "task_id")
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "owner_member_id", nullable = false)
    private UUID ownerMemberId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Column(name = "created_by_member_id", nullable = false, updatable = false)
    private UUID createdByMemberId;

    protected Task() {
    }

    /** A new task always starts as To do (FR-12, BR-11). The creator comes from the session (BR-12). */
    Task(UUID projectId, String title, String description, UUID ownerMemberId, LocalDate dueDate,
         UUID createdByMemberId) {
        this.projectId = projectId;
        this.title = title;
        this.description = description;
        this.ownerMemberId = ownerMemberId;
        this.dueDate = dueDate;
        this.status = TaskStatus.TO_DO;
        this.createdByMemberId = createdByMemberId;
    }

    UUID getId() {
        return id;
    }

    UUID getProjectId() {
        return projectId;
    }

    String getTitle() {
        return title;
    }

    String getDescription() {
        return description;
    }

    UUID getOwnerMemberId() {
        return ownerMemberId;
    }

    LocalDate getDueDate() {
        return dueDate;
    }

    TaskStatus getStatus() {
        return status;
    }

    UUID getCreatedByMemberId() {
        return createdByMemberId;
    }

    void changeStatus(TaskStatus status) {
        this.status = status;
    }
}
