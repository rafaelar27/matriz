package com.matriz.workspaces;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matriz.accesscontrol.AccessPolicy;
import com.matriz.accesscontrol.Actor;
import com.matriz.accesscontrol.Role;
import com.matriz.accounts.AccountDirectory;
import com.matriz.accounts.MembershipProvider.Membership;
import com.matriz.email.InvitationMailer;
import com.matriz.email.InvitationNotSentException;
import com.matriz.shared.ApiError;
import com.matriz.shared.ApiException;

/**
 * Invitations (CMP-02; FR-03, FR-04, US-03, INC-17; decisions of 2026-11-19 Q1 to Q11, QH to QM). Workspace
 * Administrators invite by email with a role; the invitee accepts while signed in with that email (DA-01, BR-24).
 */
@Service
class InvitationService {

    static final String FORMER_MEMBER = "Former member";
    static final String WORKSPACE_NOT_FOUND = "The workspace was not found.";
    private static final String INVITATION_NOT_FOUND = "The invitation was not found.";
    private static final String NOT_SENT = "The invitation could not be sent.";

    private final InvitationRepository invitations;
    private final MemberRepository members;
    private final WorkspaceRepository workspaces;
    private final MemberDirectory directory;
    private final AccountDirectory accounts;
    private final AccessPolicy policy;
    private final InvitationMailer mailer;
    private final AdminGuard guard;

    InvitationService(InvitationRepository invitations, MemberRepository members, WorkspaceRepository workspaces,
                      MemberDirectory directory, AccountDirectory accounts, AccessPolicy policy,
                      InvitationMailer mailer, AdminGuard guard) {
        this.guard = guard;
        this.invitations = invitations;
        this.members = members;
        this.workspaces = workspaces;
        this.directory = directory;
        this.accounts = accounts;
        this.policy = policy;
        this.mailer = mailer;
    }

    /**
     * createInvitation. Permission first, then the fields, then EC-01, then store and send. The email is sent last,
     * inside the transaction: if it cannot be sent, nothing is kept (decision QJ).
     */
    @Transactional
    InvitationView create(UUID callerAccountId, UUID workspaceId, InvitationFields body) {
        Actor actor = admin(callerAccountId, workspaceId);
        guard.lockAndRecheck(workspaceId, actor); // S-01: still an administrator inside this transaction
        String email = AccountDirectory.validEmail(body.email()).orElseThrow(() -> ApiException.validation(
                NOT_SENT, "email", "Enter a valid email address."));
        Role role = Role.fromLabel(body.role()).orElseThrow(() -> ApiException.validation(
                NOT_SENT, "role", "Choose one of the four roles."));
        Optional<UUID> account = accounts.accountIdByEmail(email);
        if (account.flatMap(a -> members.findMembership(a, workspaceId)).filter(Member::isActive).isPresent()) {
            throw ApiException.conflict("This person is already a member of the workspace.", "email");   // EC-01
        }
        Workspace workspace = workspaces.getReferenceById(workspaceId);
        Invitation saved = invitations.saveAndFlush(new Invitation(workspace, email, role, actor.memberId()));
        String inviter = directory.namesOf(List.of(actor.memberId())).getOrDefault(actor.memberId(), FORMER_MEMBER);
        try {
            mailer.send(new InvitationMailer.Invitation(saved.getId(), email, workspace.getName(), inviter,
                    role.label()));
        } catch (InvitationNotSentException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, new ApiError("email_not_sent",
                    "The invitation email could not be sent. Try again later."));
        }
        return view(saved, inviter);
    }

    /** listInvitations: pending only, newest first. */
    @Transactional(readOnly = true)
    List<InvitationView> pending(UUID callerAccountId, UUID workspaceId) {
        admin(callerAccountId, workspaceId);
        List<Invitation> found = invitations.findPendingInWorkspace(workspaceId);
        Map<UUID, String> names = directory.namesOf(found.stream().map(Invitation::getInvitedByMemberId)
                .filter(Objects::nonNull).distinct().toList());
        return found.stream().map(i -> view(i, inviterName(i, names))).toList();
    }

    /** cancelInvitation: the row is kept with status CANCELLED (QK). */
    @Transactional
    void cancel(UUID callerAccountId, UUID workspaceId, UUID invitationId) {
        guard.lockAndRecheck(workspaceId, admin(callerAccountId, workspaceId)); // S-01
        invitations.findPendingInWorkspace(invitationId, workspaceId)
                .orElseThrow(() -> ApiException.notFound(INVITATION_NOT_FOUND))
                .cancel();
    }

    /** listMyInvitations: pending invitations to the caller's email, deleted workspaces left out (PD-24, QM). */
    @Transactional(readOnly = true)
    List<MyInvitationView> mine(String callerEmail) {
        List<Invitation> found = invitations.findPendingForEmail(callerEmail);
        Map<UUID, String> names = directory.namesOf(found.stream().map(Invitation::getInvitedByMemberId)
                .filter(Objects::nonNull).distinct().toList());
        return found.stream().map(i -> new MyInvitationView(i.getId(), i.getWorkspace().getId(),
                i.getWorkspace().getName(), i.getRole().label(), inviterName(i, names), i.getCreatedAt())).toList();
    }

    /**
     * acceptInvitation. One transaction: the membership (new, or a deactivated one reactivated, Q8), the invitation
     * ACCEPTED, and the other pending invitations to the same address in that workspace CANCELLED (QK, EC-02).
     */
    @Transactional
    Membership accept(UUID callerAccountId, String callerEmail, UUID invitationId) {
        UUID workspaceId = invitations.findWorkspaceOfPending(invitationId, callerEmail)
                .orElseThrow(() -> ApiException.notFound(INVITATION_NOT_FOUND));
        List<Invitation> pending = invitations.lockPendingForEmail(workspaceId, callerEmail);
        Invitation invitation = pending.stream().filter(i -> i.getId().equals(invitationId)).findFirst()
                .orElseThrow(() -> ApiException.notFound(INVITATION_NOT_FOUND)); // accepted or cancelled meanwhile
        Workspace workspace = invitation.getWorkspace();
        Optional<Member> existing = members.findMembership(callerAccountId, workspace.getId());
        if (existing.filter(Member::isActive).isPresent()) {
            throw ApiException.conflict("You are already a member of this workspace.", "invitation_id");
        }
        Member member;
        try {
            member = existing.map(m -> {
                m.reactivate(invitation.getRole());
                return m;
            }).orElseGet(() -> members.saveAndFlush(new Member(workspace, callerAccountId, invitation.getRole())));
        } catch (DataIntegrityViolationException e) {
            // Another invitation to this workspace was accepted at the same moment (BR-05, member_workspace_account_uk).
            throw ApiException.conflict("You are already a member of this workspace.", "invitation_id");
        }
        invitation.accept();
        pending.stream().filter(i -> i != invitation).forEach(Invitation::cancel);
        return new Membership(member.getId(), workspace.getId(), workspace.getName(), member.getRole().label());
    }

    /** The caller must be an active Workspace Administrator of a workspace that exists and is not deleted. */
    private Actor admin(UUID callerAccountId, UUID workspaceId) {
        Actor actor = policy.requireWorkspaceAccess(directory.actorIn(callerAccountId, workspaceId),
                WORKSPACE_NOT_FOUND);
        policy.checkManageWorkspace(actor);
        return actor;
    }

    private static String inviterName(Invitation invitation, Map<UUID, String> names) {
        UUID by = invitation.getInvitedByMemberId();
        return by == null ? FORMER_MEMBER : names.getOrDefault(by, FORMER_MEMBER);
    }

    private static InvitationView view(Invitation i, String inviterName) {
        return new InvitationView(i.getId(), i.getEmail(), i.getRole().label(), statusLabel(i.getStatus()),
                i.getCreatedAt(), inviterName);
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case Invitation.ACCEPTED -> "Accepted";
            case Invitation.CANCELLED -> "Cancelled";
            default -> "Pending";
        };
    }

    /** openapi.yaml Invitation. */
    record InvitationView(UUID invitationId, String email, String role, String status, OffsetDateTime createdAt,
                          String invitedByName) {
    }

    /** openapi.yaml MyInvitation. */
    record MyInvitationView(UUID invitationId, UUID workspaceId, String workspaceName, String role,
                            String invitedByName, OffsetDateTime createdAt) {
    }
}
