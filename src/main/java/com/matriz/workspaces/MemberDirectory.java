package com.matriz.workspaces;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matriz.accesscontrol.Actor;

/**
 * Public interface of CMP-02 for other modules: a person's membership and role in a workspace. Read from the
 * database on every call, so role changes and deactivation apply to the next request (SP-03, SP-04).
 */
@Service
public class MemberDirectory {

    private final MemberRepository members;

    MemberDirectory(MemberRepository members) {
        this.members = members;
    }

    /** The account's membership in the workspace, as facts for Access Control. Empty if it is not a member. */
    @Transactional(readOnly = true)
    public Optional<Actor> actorIn(UUID accountId, UUID workspaceId) {
        return members.findMembership(accountId, workspaceId)
                .map(m -> new Actor(m.getId(), m.getRole(), m.isActive()));
    }

    /** The account's active memberships, for the signed-in profile. Deactivated memberships give no access. */
    @Transactional(readOnly = true)
    public List<Membership> activeMembershipsOf(UUID accountId) {
        return members.findAllByAccount(accountId).stream()
                .filter(Member::isActive)
                .map(m -> new Membership(m.getId(), m.getWorkspace().getId(), m.getWorkspace().getName(),
                        m.getRole().label()))
                .toList();
    }

    /** A membership as shown in the signed-in profile. */
    public record Membership(UUID memberId, UUID workspaceId, String workspaceName, String role) {
    }
}
