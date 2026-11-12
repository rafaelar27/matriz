package com.matriz.tasks;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * A member assigned to a task in addition to its owner (database.md: TaskAssignee; FR-22, BR-22). Read through JPA;
 * written only with {@link TaskAssigneeRepository#insert}, so the database's primary key decides uniqueness.
 */
@Entity
@Table(name = "task_assignee")
@IdClass(TaskAssignee.Key.class)
class TaskAssignee {

    @Id
    @Column(name = "task_id")
    private UUID taskId;

    @Id
    @Column(name = "member_id")
    private UUID memberId;

    @Column(name = "assigned_at", insertable = false, updatable = false)
    private Instant assignedAt;

    protected TaskAssignee() {
    }

    UUID getTaskId() {
        return taskId;
    }

    UUID getMemberId() {
        return memberId;
    }

    Instant getAssignedAt() {
        return assignedAt;
    }

    /** Composite key (task, member). */
    static class Key implements Serializable {

        private UUID taskId;
        private UUID memberId;

        protected Key() {
        }

        Key(UUID taskId, UUID memberId) {
            this.taskId = taskId;
            this.memberId = memberId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(taskId, k.taskId) && Objects.equals(memberId, k.memberId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(taskId, memberId);
        }
    }
}
