package com.matriz.workspaces;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.matriz.accesscontrol.AccessPolicy;
import com.matriz.accounts.SignedInAccount;
import com.matriz.shared.ApiException;
import com.matriz.shared.Ids;

/** listWorkspaceMembers (openapi.yaml): active members, for choosing a project owner (FR-06). */
@RestController
class WorkspaceController {

    private static final String NOT_FOUND = "The workspace was not found.";

    private final MemberDirectory members;
    private final AccessPolicy policy;

    WorkspaceController(MemberDirectory members, AccessPolicy policy) {
        this.members = members;
        this.policy = policy;
    }

    @GetMapping("/workspaces/{workspaceId}/members")
    List<MemberDirectory.WorkspaceMember> members(@AuthenticationPrincipal SignedInAccount caller,
                                                  @PathVariable String workspaceId) {
        UUID workspace = Ids.parse(workspaceId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        policy.requireWorkspaceAccess(members.actorIn(caller.accountId(), workspace), NOT_FOUND);
        return members.activeMembersOf(workspace);
    }
}
