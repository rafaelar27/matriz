package com.matriz.workspaces;

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

/** One account's membership in one workspace (database.md: Member). Owned by CMP-02. */
@Entity
@Table(name = "member")
class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "member_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @Column(name = "account_id")
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    private Role role;

    private String status;

    protected Member() {
    }

    /** A new active membership (status ACTIVE, as the column default). */
    Member(Workspace workspace, UUID accountId, Role role) {
        this.workspace = workspace;
        this.accountId = accountId;
        this.role = role;
        this.status = "ACTIVE";
    }

    UUID getId() {
        return id;
    }

    Workspace getWorkspace() {
        return workspace;
    }

    UUID getAccountId() {
        return accountId;
    }

    Role getRole() {
        return role;
    }

    boolean isActive() {
        return "ACTIVE".equals(status);
    }

    /** updateMemberRole (FR-04, US-04); applies to the member's next request (SP-03). */
    void changeRole(Role newRole) {
        this.role = newRole;
    }

    /** Accepting an invitation for a deactivated membership makes it active again with the invited role (Q8). */
    void reactivate(Role invitedRole) {
        this.role = invitedRole;
        this.status = "ACTIVE";
    }
}
