package com.matriz.workspaces;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.matriz.accounts.MembershipProvider.Membership;
import com.matriz.accounts.SignedInAccount;
import com.matriz.shared.ApiException;
import com.matriz.shared.Ids;

/**
 * createInvitation, listInvitations, cancelInvitation, listMyInvitations and acceptInvitation (openapi.yaml; FR-03,
 * INC-17). Identity always comes from the session: the inviter's membership and the invitee's email.
 */
@RestController
class InvitationController {

    private static final String INVITATION_NOT_FOUND = "The invitation was not found.";

    private final InvitationService service;

    InvitationController(InvitationService service) {
        this.service = service;
    }

    @PostMapping("/workspaces/{workspaceId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    InvitationService.InvitationView create(@AuthenticationPrincipal SignedInAccount caller,
                                            @PathVariable String workspaceId, @RequestBody InvitationFields body) {
        return service.create(caller.accountId(), workspace(workspaceId), body);
    }

    @GetMapping("/workspaces/{workspaceId}/invitations")
    List<InvitationService.InvitationView> pending(@AuthenticationPrincipal SignedInAccount caller,
                                                   @PathVariable String workspaceId) {
        return service.pending(caller.accountId(), workspace(workspaceId));
    }

    @DeleteMapping("/workspaces/{workspaceId}/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@AuthenticationPrincipal SignedInAccount caller, @PathVariable String workspaceId,
                @PathVariable String invitationId) {
        UUID workspace = workspace(workspaceId);
        service.cancel(caller.accountId(), workspace, invitation(invitationId));
    }

    @GetMapping("/invitations")
    List<InvitationService.MyInvitationView> mine(@AuthenticationPrincipal SignedInAccount caller) {
        return service.mine(caller.email());
    }

    /** No body is defined; an empty body or none at all is expected. */
    @PostMapping("/invitations/{invitationId}/accept")
    Membership accept(@AuthenticationPrincipal SignedInAccount caller, @PathVariable String invitationId) {
        return service.accept(caller.accountId(), caller.email(), invitation(invitationId));
    }

    private static UUID workspace(String id) {
        return Ids.parse(id).orElseThrow(() -> ApiException.notFound(InvitationService.WORKSPACE_NOT_FOUND));
    }

    private static UUID invitation(String id) {
        return Ids.parse(id).orElseThrow(() -> ApiException.notFound(INVITATION_NOT_FOUND));
    }
}
