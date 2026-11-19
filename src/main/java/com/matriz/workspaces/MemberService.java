package com.matriz.workspaces;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matriz.accesscontrol.AccessPolicy;
import com.matriz.accesscontrol.Actor;
import com.matriz.accesscontrol.Role;
import com.matriz.shared.ApiException;

/**
 * Changing roles and removing members (CMP-02; FR-03, FR-04, US-04, INC-18; decisions of 2026-11-19 QA to QG). Any
 * active Workspace Administrator, also for themselves (QG); a workspace always keeps one (SP-07, BR-04, EC-03).
 */
@Service
class MemberService {

    static final String LAST_ADMINISTRATOR = "A workspace must keep at least one active Workspace Administrator.";
    private static final String MEMBER_NOT_FOUND = "The member was not found.";
    private static final String NOT_SAVED = "The member could not be changed.";

    private final MemberRepository members;
    private final MemberDirectory directory;
    private final AccessPolicy policy;
    private final AdminGuard guard;

    MemberService(MemberRepository members, MemberDirectory directory, AccessPolicy policy, AdminGuard guard) {
        this.members = members;
        this.directory = directory;
        this.policy = policy;
        this.guard = guard;
    }

    /** updateMemberRole. */
    @Transactional
    MemberDirectory.WorkspaceMember changeRole(UUID callerAccountId, UUID workspaceId, UUID memberId,
                                               MemberRoleFields body) {
        Actor actor = admin(callerAccountId, workspaceId);
        Role role = Role.fromLabel(body.role()).orElseThrow(() -> ApiException.validation(
                NOT_SAVED, "role", "Choose one of the four roles."));
        List<Member> admins = guard.lockAndRecheck(workspaceId, actor);
        Member target = target(workspaceId, memberId);
        if (leavesNoAdministrator(admins, target, role)) {
            throw ApiException.conflict(LAST_ADMINISTRATOR, "role");
        }
        target.changeRole(role);
        String name = directory.namesOf(List.of(target.getId())).getOrDefault(target.getId(), "");
        return new MemberDirectory.WorkspaceMember(target.getId(), name, role.label());
    }

    /**
     * removeMember. In one transaction: what the member owns goes to the removing administrator, or, when
     * administrators remove themselves, to the administrator they chose (QB, QC); then the member row is deleted and
     * the database empties or deletes the rows that refer to it (V7: QD, QF, "Former member").
     */
    @Transactional
    void remove(UUID callerAccountId, UUID workspaceId, UUID memberId, Optional<UUID> newOwnerMemberId) {
        Actor actor = admin(callerAccountId, workspaceId);
        List<Member> admins = guard.lockAndRecheck(workspaceId, actor);
        Member target = target(workspaceId, memberId);
        if (leavesNoAdministrator(admins, target, null)) {
            throw ApiException.conflict(LAST_ADMINISTRATOR, "member_id");
        }
        UUID newOwner = newOwner(actor, target, admins, newOwnerMemberId);
        members.flush();
        members.addToOwnedProjects(target.getId(), newOwner);
        members.transferTasks(target.getId(), newOwner);
        members.transferProjects(target.getId(), newOwner);
        members.delete(target);
        members.flush();
    }

    /**
     * Whether the change leaves the workspace with no active Workspace Administrator (SP-07, BR-04, EC-03).
     *
     * @param activeAdmins the workspace's active Workspace Administrators, locked, before the change
     * @param target       the member being changed or removed
     * @param newRole      the target's new role, or {@code null} when the target is being removed
     */
    static boolean leavesNoAdministrator(List<Member> activeAdmins, Member target, Role newRole) {
        if (newRole == Role.WORKSPACE_ADMINISTRATOR) {
            return false; // the target is (or stays) an administrator: never fewer administrators than before
        }
        // The target is removed or gets another role: someone other than the target must remain.
        return activeAdmins.stream().noneMatch(m -> !m.getId().equals(target.getId()));
    }

    /** Who takes over what the removed member owns (QB, QC; self-removal: the administrator picked in the dialog). */
    private static UUID newOwner(Actor actor, Member target, List<Member> admins, Optional<UUID> chosen) {
        boolean self = target.getId().equals(actor.memberId());
        if (!self) {
            if (chosen.isPresent()) {
                throw ApiException.validation(NOT_SAVED, "new_owner_member_id",
                        "Only needed when you remove yourself.");
            }
            return actor.memberId();
        }
        UUID to = chosen.orElseThrow(() -> ApiException.validation(NOT_SAVED, "new_owner_member_id",
                "Choose the Workspace Administrator who takes over what you own."));
        boolean otherActiveAdmin = admins.stream().anyMatch(m -> m.getId().equals(to) && !m.getId().equals(target.getId()));
        if (!otherActiveAdmin) {
            throw ApiException.validation(NOT_SAVED, "new_owner_member_id",
                    "Choose another active Workspace Administrator of this workspace.");
        }
        return to;
    }

    private Member target(UUID workspaceId, UUID memberId) {
        return members.findById(memberId)
                .filter(m -> m.getWorkspace().getId().equals(workspaceId))
                .orElseThrow(() -> ApiException.notFound(MEMBER_NOT_FOUND));
    }

    private Actor admin(UUID callerAccountId, UUID workspaceId) {
        Actor actor = policy.requireWorkspaceAccess(directory.actorIn(callerAccountId, workspaceId),
                InvitationService.WORKSPACE_NOT_FOUND);
        policy.checkManageWorkspace(actor);
        return actor;
    }
}
