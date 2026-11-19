package com.matriz.workspaces;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    /** A workspace's pending invitations, newest first (listInvitations). */
    @Query("select i from Invitation i where i.workspace.id = :workspaceId and i.status = 'PENDING'"
            + " order by i.createdAt desc, i.id")
    List<Invitation> findPendingInWorkspace(UUID workspaceId);

    /** One pending invitation of this workspace (cancelInvitation). */
    @Query("select i from Invitation i where i.id = :invitationId and i.workspace.id = :workspaceId"
            + " and i.status = 'PENDING'")
    Optional<Invitation> findPendingInWorkspace(UUID invitationId, UUID workspaceId);

    /** Pending invitations to this address in workspaces that are not deleted (listMyInvitations). */
    @Query("select i from Invitation i join fetch i.workspace w where lower(i.email) = lower(:email)"
            + " and i.status = 'PENDING' and w.deletedAt is null order by i.createdAt desc, i.id")
    List<Invitation> findPendingForEmail(String email);

    /** The workspace of a pending invitation to this address, if any (first step of acceptInvitation; not locked). */
    @Query("select i.workspace.id from Invitation i where i.id = :invitationId and lower(i.email) = lower(:email)"
            + " and i.status = 'PENDING' and i.workspace.deletedAt is null")
    Optional<UUID> findWorkspaceOfPending(UUID invitationId, String email);

    /**
     * Every pending invitation to this address in the workspace, locked in a fixed order. Accepts of any of them at
     * the same moment then run one after the other: the second waits, and afterwards no longer finds its invitation
     * pending (EC-02, QK). Locking only the accepted invitation could deadlock, because the first accept cancels the
     * others while the second waits on the unique member key.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invitation i where i.workspace.id = :workspaceId and lower(i.email) = lower(:email)"
            + " and i.status = 'PENDING' and i.workspace.deletedAt is null order by i.id")
    List<Invitation> lockPendingForEmail(UUID workspaceId, String email);
}
