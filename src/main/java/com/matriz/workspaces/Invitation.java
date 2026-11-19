package com.matriz.workspaces;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.matriz.accesscontrol.Role;

/**
 * An invitation to join a workspace with a role (database.md: Invitation; FR-03, INC-17). Owned by CMP-02. Status
 * PENDING, then ACCEPTED or CANCELLED; the row is never deleted (decision QK).
 */
@Entity
@Table(name = "invitation")
class Invitation {

    static final String PENDING = "PENDING";
    static final String ACCEPTED = "ACCEPTED";
    static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "invitation_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    private String email;

    @Enumerated(EnumType.STRING)
    private Role role;

    /** Empty when the inviter was removed from the workspace (V7, shown as "Former member"). */
    @Column(name = "invited_by_member_id")
    private UUID invitedByMemberId;

    private String status;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    protected Invitation() {
    }

    /** A new pending invitation; {@code email} is already in its BR-24 form. */
    Invitation(Workspace workspace, String email, Role role, UUID invitedByMemberId) {
        this.workspace = workspace;
        this.email = email;
        this.role = role;
        this.invitedByMemberId = invitedByMemberId;
        this.status = PENDING;
        this.createdAt = OffsetDateTime.now();
    }

    UUID getId() {
        return id;
    }

    Workspace getWorkspace() {
        return workspace;
    }

    String getEmail() {
        return email;
    }

    Role getRole() {
        return role;
    }

    UUID getInvitedByMemberId() {
        return invitedByMemberId;
    }

    String getStatus() {
        return status;
    }

    OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    void accept() {
        this.status = ACCEPTED;
    }

    void cancel() {
        this.status = CANCELLED;
    }
}
