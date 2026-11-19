package com.matriz.workspaces;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.matriz.accesscontrol.Actor;
import com.matriz.accesscontrol.Role;
import com.matriz.shared.ApiException;

/**
 * Second check for every workspace-management change (SP-03, SP-07; review R-04, security audit S-01). The caller's
 * role is first read without a lock; inside the change's own transaction the workspace's active Workspace
 * Administrators are locked in a fixed order and the caller must still be one of them. An administrator demoted at
 * the same moment therefore changes nothing, and two changes to the administrators run one after the other.
 */
@Component
class AdminGuard {

    private final MemberRepository members;

    AdminGuard(MemberRepository members) {
        this.members = members;
    }

    /** The locked active administrators; 403 when the caller is no longer one of them. */
    @Transactional(propagation = Propagation.MANDATORY)
    List<Member> lockAndRecheck(UUID workspaceId, Actor actor) {
        List<Member> admins = members.lockActiveWithRole(workspaceId, Role.WORKSPACE_ADMINISTRATOR);
        if (admins.stream().noneMatch(m -> m.getId().equals(actor.memberId()))) {
            throw ApiException.forbidden("Only Workspace Administrators can manage this workspace and its members.");
        }
        return admins;
    }
}
