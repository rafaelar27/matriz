package com.matriz.workspaces;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.matriz.accesscontrol.Role;

/**
 * Memberships. A deleted workspace (INC-19, soft delete) has no memberships for anyone: every module reaches a
 * workspace through {@link MemberDirectory}, so its projects, tasks and comments give 404 too (decision Q13).
 */
interface MemberRepository extends JpaRepository<Member, UUID> {

    @Query("select m from Member m where m.accountId = :accountId and m.workspace.id = :workspaceId"
            + " and m.workspace.deletedAt is null")
    Optional<Member> findMembership(UUID accountId, UUID workspaceId);

    @Query("select m from Member m join fetch m.workspace w where m.accountId = :accountId and w.deletedAt is null"
            + " order by w.name")
    List<Member> findAllByAccount(UUID accountId);

    @Query("select m from Member m where m.workspace.id = :workspaceId and m.workspace.deletedAt is null")
    List<Member> findAllByWorkspace(UUID workspaceId);

    /**
     * The active Workspace Administrators of a workspace, locked in a fixed order. Two administrators changing each
     * other at the same moment are handled one after the other, so the last one is never removed or demoted
     * (SP-07, BR-04, EC-03).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.workspace.id = :workspaceId and m.role = :role and m.status = 'ACTIVE'"
            + " order by m.id")
    List<Member> lockActiveWithRole(UUID workspaceId, Role role);

    /**
     * Before a member is removed: the new owner joins every project where the removed member owns the project or a
     * task, so the transferred tasks keep an owner who takes part in the project (FR-10, decisions QB, QC). The
     * projects and tasks modules are reached through the shared tables, not their code, to avoid a dependency cycle
     * (architecture decision rule 3).
     */
    @Modifying
    @Query(nativeQuery = true, value = "INSERT INTO project_member (project_id, member_id)"
            + " SELECT t.project_id, :to FROM task t WHERE t.owner_member_id = :from"
            + " UNION SELECT p.project_id, :to FROM project p WHERE p.owner_member_id = :from"
            + " ON CONFLICT DO NOTHING")
    int addToOwnedProjects(UUID from, UUID to);

    @Modifying
    @Query(nativeQuery = true, value = "UPDATE task SET owner_member_id = :to WHERE owner_member_id = :from")
    int transferTasks(UUID from, UUID to);

    @Modifying
    @Query(nativeQuery = true, value = "UPDATE project SET owner_member_id = :to WHERE owner_member_id = :from")
    int transferProjects(UUID from, UUID to);
}
