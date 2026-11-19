package com.matriz.workspaces;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.matriz.accesscontrol.AccessPolicy;
import com.matriz.accesscontrol.Actor;
import com.matriz.accounts.SignedInAccount;
import com.matriz.shared.ApiException;
import com.matriz.shared.Ids;

/**
 * createWorkspace (FR-02), listWorkspaceMembers (active members, FR-06), updateWorkspace (FR-23), deleteWorkspace
 * (FR-24), updateMemberRole (FR-04) and removeMember (FR-03) (openapi.yaml; INC-14, INC-18, INC-19).
 */
@RestController
class WorkspaceController {

    private static final String NOT_FOUND = "The workspace was not found.";
    private static final String MEMBER_NOT_FOUND = "The member was not found.";

    private final MemberDirectory members;
    private final AccessPolicy policy;
    private final WorkspaceService service;
    private final MemberService memberService;

    WorkspaceController(MemberDirectory members, AccessPolicy policy, WorkspaceService service,
                        MemberService memberService) {
        this.members = members;
        this.policy = policy;
        this.service = service;
        this.memberService = memberService;
    }

    @PostMapping("/workspaces")
    @ResponseStatus(HttpStatus.CREATED)
    WorkspaceService.WorkspaceView create(@AuthenticationPrincipal SignedInAccount caller,
                                          @RequestBody WorkspaceFields body) {
        return service.create(caller.accountId(), body);
    }

    @GetMapping("/workspaces/{workspaceId}/members")
    List<MemberDirectory.WorkspaceMember> members(@AuthenticationPrincipal SignedInAccount caller,
                                                  @PathVariable String workspaceId) {
        UUID workspace = workspace(workspaceId);
        policy.requireWorkspaceAccess(members.actorIn(caller.accountId(), workspace), NOT_FOUND);
        return members.activeMembersOf(workspace);
    }

    @PatchMapping("/workspaces/{workspaceId}")
    WorkspaceService.WorkspaceSummary rename(@AuthenticationPrincipal SignedInAccount caller,
                                             @PathVariable String workspaceId, @RequestBody WorkspaceFields body) {
        UUID workspace = workspace(workspaceId);
        return service.rename(manageable(caller, workspace), workspace, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal SignedInAccount caller, @PathVariable String workspaceId,
                @RequestBody WorkspaceDeleteFields body) {
        UUID workspace = workspace(workspaceId);
        service.delete(manageable(caller, workspace), workspace, body);
    }

    @PatchMapping("/workspaces/{workspaceId}/members/{memberId}")
    MemberDirectory.WorkspaceMember changeRole(@AuthenticationPrincipal SignedInAccount caller,
                                               @PathVariable String workspaceId, @PathVariable String memberId,
                                               @RequestBody MemberRoleFields body) {
        return memberService.changeRole(caller.accountId(), workspace(workspaceId), member(memberId), body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@AuthenticationPrincipal SignedInAccount caller, @PathVariable String workspaceId,
                @PathVariable String memberId,
                @RequestParam(name = "new_owner_member_id", required = false) String newOwnerMemberId) {
        UUID workspace = workspace(workspaceId);
        UUID member = member(memberId);
        Optional<UUID> newOwner = Optional.ofNullable(newOwnerMemberId).map(id -> Ids.parse(id).orElseThrow(() ->
                ApiException.validation("The member could not be changed.", "new_owner_member_id",
                        "Choose another active Workspace Administrator of this workspace.")));
        memberService.remove(caller.accountId(), workspace, member, newOwner);
    }

    /**
     * The caller must be an active Workspace Administrator of the (not deleted) workspace (Q1). The service checks it
     * again under a lock inside its own transaction (S-01).
     */
    private Actor manageable(SignedInAccount caller, UUID workspace) {
        Actor actor = policy.requireWorkspaceAccess(members.actorIn(caller.accountId(), workspace), NOT_FOUND);
        policy.checkManageWorkspace(actor);
        return actor;
    }

    private static UUID workspace(String id) {
        return Ids.parse(id).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    private static UUID member(String id) {
        return Ids.parse(id).orElseThrow(() -> ApiException.notFound(MEMBER_NOT_FOUND));
    }
}
