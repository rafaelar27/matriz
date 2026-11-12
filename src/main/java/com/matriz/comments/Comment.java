package com.matriz.comments;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A comment on a task (database.md: Comment; FR-16). Never edited or deleted in the MVP, except with its task. */
@Entity
@Table(name = "comment")
class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "comment_id")
    private UUID id;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "author_member_id", nullable = false, updatable = false)
    private UUID authorMemberId;

    @Column(nullable = false, updatable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Comment() {
    }

    /** The author comes from the session and the time from the server, never from the client. */
    Comment(UUID taskId, UUID authorMemberId, String body, Instant createdAt) {
        this.taskId = taskId;
        this.authorMemberId = authorMemberId;
        this.body = body;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getTaskId() {
        return taskId;
    }

    UUID getAuthorMemberId() {
        return authorMemberId;
    }

    String getBody() {
        return body;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
