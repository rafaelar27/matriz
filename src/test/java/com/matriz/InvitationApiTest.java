package com.matriz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import com.matriz.email.InvitationMailer;
import com.matriz.email.InvitationNotSentException;

/**
 * Invitations through direct API requests (INC-17; US-03; FR-03, FR-04, AC-03.1 to AC-03.4, EC-01, EC-02, BR-05,
 * BR-24, SP-01, SP-02, SP-03, SP-04, SP-07, NFR-01). openapi.yaml 0.13.0-draft, decisions of 2026-11-19: Q1, Q3, Q4,
 * Q7, Q8, Q10, QJ, QK. The mail sender is replaced by a mock, so no email is ever sent. Rolling back after an
 * email failure (QJ) and simultaneous accepts need committed transactions: see InvitationTransactionTest.
 *
 * <p>The draft acceptance criteria AC-INC17.1 to .14 are numbered as listed in the report of this test run:
 * .1 create, .2 email rule, .3 role rule, .4 refused fields, .5 permissions, .6 authentication and CSRF,
 * .7 EC-01 and Q8, .8 QJ, .9 sent email content, .10 listInvitations, .11 cancelInvitation, .12 listMyInvitations,
 * .13 acceptInvitation, .14 reactivation (Q8) and accepting twice.
 */
class InvitationApiTest extends WorkspaceAdminTestSupport {

    @MockitoBean
    InvitationMailer mailer;

    private static String body(String email, String role) {
        return "{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}";
    }

    private ResultActions invite(MockHttpSession s, String workspace, String json) throws Exception {
        return send(post("/workspaces/" + workspace + "/invitations"), s, json);
    }

    private String inviteOk(MockHttpSession s, String workspace, String email, String role) throws Exception {
        return id(invite(s, workspace, body(email, role)).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$.invitation_id");
    }

    private ResultActions accept(MockHttpSession s, String invitationId) throws Exception {
        return send(post("/invitations/" + invitationId + "/accept"), s, null);
    }

    private long invitations(String workspace) {
        return rowsOf("invitation", workspace);
    }

    private String statusOf(String invitationId) {
        return text("SELECT status FROM invitation WHERE invitation_id = ?::uuid", invitationId);
    }

    private void assertRefused(MockHttpSession s, String json, String field) throws Exception {
        long before = invitations(ACME);
        clearInvocations(mailer); // earlier valid invitations in the same test did send
        invite(s, ACME, json).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.details[0].field").value(field));
        assertThat(invitations(ACME)).as("nothing stored for " + json).isEqualTo(before);
        verify(mailer, never()).send(any());
    }

    // --- AC-INC17.1 and AC-INC17.9: create, store, send ----------------------------------------------------------

    @Test
    @DisplayName("AC-INC17.1, AC-03.1, FR-03 · a Workspace Administrator invites: 201 with the Invitation, stored PENDING with the session member as inviter, email trimmed and lower-cased")
    void createStoresPendingInvitation() throws Exception {
        MockHttpSession carla = as("carla");
        long before = invitations(ACME);
        String response = invite(carla, ACME, body("  New.Person@Example.COM  ", "Team Member"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitation_id").isNotEmpty())
                .andExpect(jsonPath("$.email").value("new.person@example.com"))
                .andExpect(jsonPath("$.role").value("Team Member"))
                .andExpect(jsonPath("$.status").value("Pending"))
                .andExpect(jsonPath("$.created_at").value(notNullValue()))
                .andExpect(jsonPath("$.invited_by_name").value("Carla Demo"))
                .andReturn().getResponse().getContentAsString();
        String invitationId = id(response, "$.invitation_id");

        assertThat(invitations(ACME)).isEqualTo(before + 1);
        assertThat(text("SELECT status FROM invitation WHERE invitation_id = ?::uuid", invitationId))
                .isEqualTo("PENDING");
        assertThat(text("SELECT email FROM invitation WHERE invitation_id = ?::uuid", invitationId))
                .isEqualTo("new.person@example.com");
        assertThat(text("SELECT role FROM invitation WHERE invitation_id = ?::uuid", invitationId))
                .isEqualTo("TEAM_MEMBER");
        assertThat(text("SELECT invited_by_member_id::text FROM invitation WHERE invitation_id = ?::uuid",
                invitationId)).isEqualTo(ACME_CARLA);
        assertThat(text("SELECT workspace_id::text FROM invitation WHERE invitation_id = ?::uuid", invitationId))
                .isEqualTo(ACME);
    }

    @Test
    @DisplayName("AC-INC17.9, FR-03, AC-03.1, QI · the mailer receives one message: stored address, workspace name, inviter name and role label, with the stored invitation id")
    void mailerReceivesTheInvitationFacts() throws Exception {
        String invitationId = inviteOk(as("carla"), ACME, "Mail.Check@Example.com", "Project Manager");

        ArgumentCaptor<InvitationMailer.Invitation> sent = ArgumentCaptor.forClass(InvitationMailer.Invitation.class);
        verify(mailer).send(sent.capture());
        assertThat(sent.getValue().to()).isEqualTo("mail.check@example.com");
        assertThat(sent.getValue().workspaceName()).isEqualTo("Acme Studio");
        assertThat(sent.getValue().inviterName()).isEqualTo("Carla Demo");
        assertThat(sent.getValue().role()).isEqualTo("Project Manager");
        assertThat(sent.getValue().invitationId()).isEqualTo(UUID.fromString(invitationId));
    }

    @Test
    @DisplayName("AC-INC17.1, FR-03 · the new invitation is listed by listInvitations, also in a new session, newest first")
    void createdInvitationsAreListedNewestFirst() throws Exception {
        MockHttpSession carla = as("carla");
        String first = inviteOk(carla, ACME, "first.person@example.com", "Viewer");
        String second = inviteOk(carla, ACME, "second.person@example.com", "Project Manager");
        jdbc.update("UPDATE invitation SET created_at = created_at - interval '1 hour' WHERE invitation_id = ?::uuid",
                first);
        fresh();

        mvc.perform(get("/workspaces/" + ACME + "/invitations").session(as("carla")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].invitation_id", contains(second, first)))
                .andExpect(jsonPath("$[0].email").value("second.person@example.com"))
                .andExpect(jsonPath("$[0].role").value("Project Manager"))
                .andExpect(jsonPath("$[0].status").value("Pending"))
                .andExpect(jsonPath("$[0].invited_by_name").value("Carla Demo"))
                .andExpect(jsonPath("$[1].role").value("Viewer"));
    }

    @Test
    @DisplayName("EC-02, AC-INC17.1 · a second invitation to an address that already has a pending one is allowed: two pending rows")
    void secondInvitationToSameAddressIsAllowed() throws Exception {
        MockHttpSession carla = as("carla");
        long before = invitations(ACME);
        inviteOk(carla, ACME, "twice@example.com", "Viewer");
        inviteOk(carla, ACME, "Twice@Example.com", "Team Member");
        assertThat(invitations(ACME)).isEqualTo(before + 2);
    }

    // --- AC-INC17.2: email rule (BR-24, Q7) -----------------------------------------------------------------------

    @Test
    @DisplayName("AC-INC17.2, AC-03.2, BR-24, Q7 · invalid email addresses give 422 on email, nothing stored, nothing sent")
    void invalidEmailsAreRefused() throws Exception {
        MockHttpSession carla = as("carla");
        String[] invalid = {
            "", "   ", "ana.example.com", "@example.com", "ana@", "@", "a@b@c.com", "an a@example.com",
            "ana@ex\\u00e4mple.com", "a\\u00f1@example.com", "ana\\u0000@example.com", "ana\\u200b@example.com"
        };
        for (String email : invalid) {
            assertRefused(carla, body(email, "Viewer"), "email");
        }
        assertRefused(carla, "{\"role\":\"Viewer\"}", "email");
        assertRefused(carla, "{\"email\":null,\"role\":\"Viewer\"}", "email");
    }

    @Test
    @DisplayName("AC-INC17.2, BR-24, Q7 · email length boundaries: 254 characters accepted (also padded with spaces), 255 refused 422 on email")
    void emailLengthBoundaries() throws Exception {
        MockHttpSession carla = as("carla");
        String max = "a".repeat(64) + "@" + "d".repeat(189);
        assertThat(max).hasSize(254);
        invite(carla, ACME, body(max, "Viewer")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(max));
        invite(carla, ACME, body("  " + max + "  ", "Viewer")).andExpect(status().isCreated());
        assertRefused(carla, body(max + "x", "Viewer"), "email");
        assertRefused(carla, body("a" + max, "Viewer"), "email");
        invite(carla, ACME, body("a@b", "Viewer")).andExpect(status().isCreated()); // shortest: text on both sides
    }

    // --- AC-INC17.3: role rule (Q10) -------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-INC17.3, AC-03.3, FR-04, Q10 · any of the four roles can be invited; stored as its code")
    void allFourRolesCanBeInvited() throws Exception {
        MockHttpSession carla = as("carla");
        String[][] roles = {{"Workspace Administrator", "WORKSPACE_ADMINISTRATOR"}, {"Project Manager",
            "PROJECT_MANAGER"}, {"Team Member", "TEAM_MEMBER"}, {"Viewer", "VIEWER"}};
        for (String[] role : roles) {
            String invitationId = id(invite(carla, ACME, body("role." + role[1].toLowerCase() + "@example.com",
                    role[0])).andExpect(status().isCreated()).andExpect(jsonPath("$.role").value(role[0]))
                    .andReturn().getResponse().getContentAsString(), "$.invitation_id");
            assertThat(text("SELECT role FROM invitation WHERE invitation_id = ?::uuid", invitationId))
                    .isEqualTo(role[1]);
        }
    }

    @Test
    @DisplayName("AC-INC17.3, AC-03.3, FR-04 · a missing, null, empty, wrong-case, translated or unknown role gives 422 on role, nothing stored")
    void invalidRolesAreRefused() throws Exception {
        MockHttpSession carla = as("carla");
        assertRefused(carla, "{\"email\":\"role.test@example.com\"}", "role");
        assertRefused(carla, "{\"email\":\"role.test@example.com\",\"role\":null}", "role");
        for (String role : new String[] {"", "team member", "TEAM_MEMBER", "viewer", "VIEWER", "Membro da equipe",
            "Administrador", "Owner", "Guest"}) {
            assertRefused(carla, body("role.test@example.com", role), "role");
        }
    }

    // --- AC-INC17.4: refused fields ---------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-INC17.4, SP-01, SP-07 · invited_by_member_id, status, workspace_id, created_at, invitation_id and unknown fields give 422 on that field, nothing stored")
    void spoofedAndUnknownFieldsAreRefused() throws Exception {
        MockHttpSession carla = as("carla");
        String base = "\"email\":\"spoof@example.com\",\"role\":\"Viewer\"";
        assertRefused(carla, "{" + base + ",\"invited_by_member_id\":\"" + ACME_BEN + "\"}", "invited_by_member_id");
        assertRefused(carla, "{" + base + ",\"status\":\"Accepted\"}", "status");
        assertRefused(carla, "{" + base + ",\"workspace_id\":\"" + NORTHWIND + "\"}", "workspace_id");
        assertRefused(carla, "{" + base + ",\"created_at\":\"2020-01-01T00:00:00Z\"}", "created_at");
        assertRefused(carla, "{" + base + ",\"invitation_id\":\"" + ACME_BEN + "\"}", "invitation_id");
        assertRefused(carla, "{" + base + ",\"colour\":\"red\"}", "colour");
    }

    @Test
    @DisplayName("AC-INC17.4 · an unreadable body is refused 400, nothing stored")
    void unreadableBodyIs400() throws Exception {
        MockHttpSession carla = as("carla");
        long before = invitations(ACME);
        invite(carla, ACME, "{\"email\":").andExpect(status().isBadRequest());
        invite(carla, ACME, "not json").andExpect(status().isBadRequest());
        send(post("/workspaces/" + ACME + "/invitations"), carla, null).andExpect(status().isBadRequest());
        assertThat(invitations(ACME)).isEqualTo(before);
    }

    // --- AC-INC17.5: permissions and workspace separation -----------------------------------------------------------

    @Test
    @DisplayName("AC-INC17.5, AC-03.4, Q4, FR-05 · a Project Manager, a Team Member and a Viewer get 403 on create, list and cancel; nothing stored or changed")
    void otherRolesAreForbidden() throws Exception {
        String pending = inviteOk(as("carla"), ACME, "pending.one@example.com", "Viewer");
        long before = invitations(ACME);
        clearInvocations(mailer); // the setup invitation sent one email
        for (String who : new String[] {"ana", "ben", "vera"}) {
            MockHttpSession s = as(who);
            invite(s, ACME, body("denied.by." + who + "@example.com", "Viewer")).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("forbidden"));
            mvc.perform(get("/workspaces/" + ACME + "/invitations").session(s)).andExpect(status().isForbidden());
            send(delete("/workspaces/" + ACME + "/invitations/" + pending), s, null)
                    .andExpect(status().isForbidden());
        }
        assertThat(invitations(ACME)).isEqualTo(before);
        assertThat(statusOf(pending)).isEqualTo("PENDING");
        verify(mailer, never()).send(any());
    }

    @Test
    @DisplayName("AC-INC17.5, NFR-01, SP-02 · a member or administrator of another workspace gets 404 on create, list and cancel; so does an unknown or invalid workspace id; nothing changes")
    void otherWorkspaceGets404() throws Exception {
        String pending = inviteOk(as("carla"), ACME, "pending.two@example.com", "Viewer");
        long before = invitations(ACME);
        for (String who : new String[] {"dan", "finn"}) { // Dan: Project Manager of Northwind; Finn: administrator of Orchard
            MockHttpSession s = as(who);
            invite(s, ACME, body("denied.by." + who + "@example.com", "Viewer")).andExpect(status().isNotFound());
            mvc.perform(get("/workspaces/" + ACME + "/invitations").session(s)).andExpect(status().isNotFound());
            send(delete("/workspaces/" + ACME + "/invitations/" + pending), s, null)
                    .andExpect(status().isNotFound());
        }
        MockHttpSession carla = as("carla");
        invite(carla, UUID.randomUUID().toString(), body("x@example.com", "Viewer")).andExpect(status().isNotFound());
        invite(carla, "not-a-uuid", body("x@example.com", "Viewer")).andExpect(status().isNotFound());
        mvc.perform(get("/workspaces/not-a-uuid/invitations").session(carla)).andExpect(status().isNotFound());
        assertThat(invitations(ACME)).isEqualTo(before);
        assertThat(statusOf(pending)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("AC-INC17.5, NFR-01 · an invitation reached through another workspace's id gives 404 on cancel and stays PENDING, even for an administrator of both workspaces")
    void invitationThroughAnotherWorkspaceIs404() throws Exception {
        MockHttpSession carla = as("carla"); // administrator of Acme Studio and Northwind Lab
        String pending = inviteOk(carla, ACME, "wrong.parent@example.com", "Viewer");
        send(delete("/workspaces/" + NORTHWIND + "/invitations/" + pending), carla, null)
                .andExpect(status().isNotFound());
        assertThat(statusOf(pending)).isEqualTo("PENDING");
        mvc.perform(get("/workspaces/" + NORTHWIND + "/invitations").session(carla))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("AC-INC17.5, Q1, SP-03 · any active Workspace Administrator invites, also one promoted after sign-in; the inviter is that member, not the first administrator")
    void secondAdministratorCanInvite() throws Exception {
        MockHttpSession ben = as("ben");
        invite(ben, ACME, body("by.ben@example.com", "Viewer")).andExpect(status().isForbidden());
        jdbc.update("UPDATE member SET role = 'WORKSPACE_ADMINISTRATOR' WHERE member_id = ?::uuid", ACME_BEN);
        fresh();
        String invitationId = id(invite(ben, ACME, body("by.ben@example.com", "Viewer"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.invited_by_name").value("Ben Demo"))
                .andReturn().getResponse().getContentAsString(), "$.invitation_id");
        assertThat(text("SELECT invited_by_member_id::text FROM invitation WHERE invitation_id = ?::uuid",
                invitationId)).isEqualTo(ACME_BEN);
        // Carla sees it too: the list is the workspace's, not the inviter's.
        mvc.perform(get("/workspaces/" + ACME + "/invitations").session(as("carla")))
                .andExpect(jsonPath("$[0].invitation_id").value(invitationId));
    }

    @Test
    @DisplayName("AC-INC17.5, SP-04 · a deactivated Workspace Administrator gets 403 on create, list and cancel")
    void deactivatedAdministratorIsForbidden() throws Exception {
        MockHttpSession carla = as("carla");
        String pending = inviteOk(carla, ACME, "pending.three@example.com", "Viewer");
        jdbc.update("UPDATE member SET status = 'DEACTIVATED' WHERE member_id = ?::uuid", ACME_CARLA);
        fresh();
        long before = invitations(ACME);
        invite(carla, ACME, body("late@example.com", "Viewer")).andExpect(status().isForbidden());
        mvc.perform(get("/workspaces/" + ACME + "/invitations").session(carla)).andExpect(status().isForbidden());
        send(delete("/workspaces/" + ACME + "/invitations/" + pending), carla, null)
                .andExpect(status().isForbidden());
        assertThat(invitations(ACME)).isEqualTo(before);
        assertThat(statusOf(pending)).isEqualTo("PENDING");
    }

    // --- AC-INC17.6: authentication and CSRF ------------------------------------------------------------------------

    @Test
    @DisplayName("AC-INC17.6, SP-01 · every invitation operation without a session gives 401 not_authenticated, nothing stored")
    void noSessionIs401() throws Exception {
        String pending = inviteOk(as("carla"), ACME, "pending.four@example.com", "Viewer");
        long before = invitations(ACME);
        mvc.perform(post("/workspaces/" + ACME + "/invitations").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("anon@example.com", "Viewer")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("not_authenticated"));
        mvc.perform(get("/workspaces/" + ACME + "/invitations")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/workspaces/" + ACME + "/invitations/" + pending).with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/invitations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/invitations/" + pending + "/accept").with(csrf())).andExpect(status().isUnauthorized());
        assertThat(invitations(ACME)).isEqualTo(before);
        assertThat(statusOf(pending)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("AC-INC17.6, SP-01 · a session ended by sign-out gets 401 session_expired on createInvitation")
    void endedSessionIs401() throws Exception {
        MockHttpSession carla = as("carla");
        mvc.perform(post("/auth/sign-out").session(carla).with(csrf())).andExpect(status().isNoContent());
        long before = invitations(ACME);
        mvc.perform(post("/workspaces/" + ACME + "/invitations").with(csrf()).with(request -> {
                    request.setRequestedSessionId("ended-session-id");
                    request.setRequestedSessionIdValid(false);
                    return request;
                }).contentType(MediaType.APPLICATION_JSON).content(body("gone@example.com", "Viewer")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("session_expired"));
        assertThat(invitations(ACME)).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-INC17.6, SP-01 · create, cancel and accept without a CSRF token give 403 csrf_invalid; nothing changes")
    void missingCsrfIs403() throws Exception {
        MockHttpSession carla = as("carla");
        String pending = inviteOk(carla, ACME, "pending.five@example.com", "Viewer");
        long before = invitations(ACME);
        mvc.perform(post("/workspaces/" + ACME + "/invitations").session(carla)
                        .contentType(MediaType.APPLICATION_JSON).content(body("nocsrf@example.com", "Viewer")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("csrf_invalid"));
        mvc.perform(delete("/workspaces/" + ACME + "/invitations/" + pending).session(carla))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("csrf_invalid"));
        mvc.perform(post("/invitations/" + pending + "/accept").session(as("dan")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("csrf_invalid"));
        assertThat(invitations(ACME)).isEqualTo(before);
        assertThat(statusOf(pending)).isEqualTo("PENDING");
    }

    // --- AC-INC17.7: EC-01 and Q8 -----------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-INC17.7, EC-01, BR-05, AC-03.1 · the address of an active member of the workspace gives 409 conflict on email (also in other letter case); nothing stored or sent")
    void activeMemberAddressIsRefused() throws Exception {
        MockHttpSession carla = as("carla");
        long before = invitations(ACME);
        for (String email : new String[] {"ben@example.com", "  BEN@Example.com ", "carla@example.com"}) {
            invite(carla, ACME, body(email, "Viewer")).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("conflict"))
                    .andExpect(jsonPath("$.details[0].field").value("email"));
        }
        assertThat(invitations(ACME)).isEqualTo(before);
        verify(mailer, never()).send(any());
        assertThat(count("SELECT count(*) FROM member WHERE workspace_id = ?::uuid", ACME)).isEqualTo(4);
    }

    @Test
    @DisplayName("AC-INC17.7, EC-01 · the address of a member of another workspace only is not refused")
    void memberOfAnotherWorkspaceCanBeInvited() throws Exception {
        invite(as("carla"), ACME, body("dan@example.com", "Team Member")).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("AC-INC17.7, Q8 · the address of a deactivated member can be invited (201)")
    void deactivatedMemberAddressCanBeInvited() throws Exception {
        jdbc.update("UPDATE member SET status = 'DEACTIVATED' WHERE member_id = ?::uuid", ACME_BEN);
        fresh();
        invite(as("carla"), ACME, body("ben@example.com", "Viewer")).andExpect(status().isCreated());
    }

    // --- AC-INC17.8: email not sent (QJ) ------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-INC17.8, QJ · when the email cannot be sent, createInvitation answers 503 email_not_sent (nothing kept: see InvitationTransactionTest)")
    void emailFailureIs503() throws Exception {
        doThrow(InvitationNotSentException.class).when(mailer).send(any());
        invite(as("carla"), ACME, body("unreachable@example.com", "Viewer"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("email_not_sent"));
    }

    // --- AC-INC17.10: listInvitations ---------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-INC17.10, QK, FR-03 · listInvitations shows pending invitations only: accepted and cancelled ones are not listed; an invitation of another workspace is not listed")
    void listShowsPendingOnly() throws Exception {
        MockHttpSession carla = as("carla");
        String kept = inviteOk(carla, ACME, "list.kept@example.com", "Viewer");
        String cancelled = inviteOk(carla, ACME, "list.cancelled@example.com", "Viewer");
        String accepted = inviteOk(carla, ACME, "dan@example.com", "Team Member");
        inviteOk(carla, NORTHWIND, "list.other@example.com", "Viewer");
        send(delete("/workspaces/" + ACME + "/invitations/" + cancelled), carla, null).andExpect(status().isNoContent());
        accept(as("dan"), accepted).andExpect(status().isOk());
        fresh();

        mvc.perform(get("/workspaces/" + ACME + "/invitations").session(carla))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].invitation_id").value(kept))
                .andExpect(jsonPath("$[0].status").value("Pending"));
    }

    @Test
    @DisplayName("AC-INC17.10, QR · the inviter of a pending invitation who was removed from the workspace is shown as Former member")
    void removedInviterIsFormerMember() throws Exception {
        String invitationId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO invitation (invitation_id, workspace_id, email, role, invited_by_member_id, status) "
                + "VALUES (?::uuid, ?::uuid, 'orphan.inviter@example.com', 'VIEWER', NULL, 'PENDING')", invitationId,
                ACME);
        fresh();
        mvc.perform(get("/workspaces/" + ACME + "/invitations").session(as("carla")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].invited_by_name").value("Former member"));
    }

    // --- AC-INC17.11: cancelInvitation --------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-INC17.11, QK · cancelInvitation answers 204 and keeps the row with status CANCELLED; it can no longer be listed, cancelled again or accepted")
    void cancelKeepsTheRow() throws Exception {
        MockHttpSession carla = as("carla");
        String invitationId = inviteOk(carla, ACME, "dan@example.com", "Team Member");
        long before = invitations(ACME);

        send(delete("/workspaces/" + ACME + "/invitations/" + invitationId), carla, null)
                .andExpect(status().isNoContent());
        assertThat(invitations(ACME)).isEqualTo(before);
        assertThat(statusOf(invitationId)).isEqualTo("CANCELLED");
        fresh();
        mvc.perform(get("/workspaces/" + ACME + "/invitations").session(carla)).andExpect(jsonPath("$", hasSize(0)));
        send(delete("/workspaces/" + ACME + "/invitations/" + invitationId), carla, null)
                .andExpect(status().isNotFound());
        MockHttpSession dan = as("dan");
        accept(dan, invitationId).andExpect(status().isNotFound());
        mvc.perform(get("/invitations").session(dan)).andExpect(jsonPath("$", hasSize(0)));
        assertThat(count("SELECT count(*) FROM member WHERE workspace_id = ?::uuid", ACME)).isEqualTo(4);
    }

    @Test
    @DisplayName("AC-INC17.11 · cancelling an accepted, unknown or malformed invitation id gives 404 and changes nothing")
    void cancelOfNonPendingIs404() throws Exception {
        MockHttpSession carla = as("carla");
        String accepted = inviteOk(carla, ACME, "dan@example.com", "Team Member");
        accept(as("dan"), accepted).andExpect(status().isOk());
        fresh();
        send(delete("/workspaces/" + ACME + "/invitations/" + accepted), carla, null).andExpect(status().isNotFound());
        assertThat(statusOf(accepted)).isEqualTo("ACCEPTED");
        send(delete("/workspaces/" + ACME + "/invitations/" + UUID.randomUUID()), carla, null)
                .andExpect(status().isNotFound());
        send(delete("/workspaces/" + ACME + "/invitations/not-a-uuid"), carla, null).andExpect(status().isNotFound());
    }

    // --- AC-INC17.12: listMyInvitations -------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-INC17.12, DA-01, BR-24 · listMyInvitations shows the caller's pending invitations, newest first, with workspace, role and inviter; others' invitations are not shown")
    void myInvitationsAreTheCallersOnly() throws Exception {
        String acme = inviteOk(as("carla"), ACME, "dan@example.com", "Project Manager");
        String orchard = inviteOk(as("finn"), ORCHARD, "Dan@Example.com", "Viewer");
        inviteOk(as("carla"), ACME, "someone.else@example.com", "Viewer");
        jdbc.update("UPDATE invitation SET created_at = created_at - interval '1 hour' WHERE invitation_id = ?::uuid",
                acme);
        fresh();

        mvc.perform(get("/invitations").session(as("dan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].invitation_id", contains(orchard, acme)))
                .andExpect(jsonPath("$[0].workspace_id").value(ORCHARD))
                .andExpect(jsonPath("$[0].workspace_name").value("Orchard Office"))
                .andExpect(jsonPath("$[0].role").value("Viewer"))
                .andExpect(jsonPath("$[0].invited_by_name").value("Finn Demo"))
                .andExpect(jsonPath("$[0].created_at").value(notNullValue()))
                .andExpect(jsonPath("$[1].workspace_name").value("Acme Studio"))
                .andExpect(jsonPath("$[1].role").value("Project Manager"));
        mvc.perform(get("/invitations").session(as("vera"))).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/invitations").session(as("ana"))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("AC-INC17.12, QK · accepted and cancelled invitations are no longer in listMyInvitations")
    void myInvitationsExcludeClosedOnes() throws Exception {
        MockHttpSession carla = as("carla");
        String cancelled = inviteOk(carla, ACME, "dan@example.com", "Viewer");
        String accepted = inviteOk(as("finn"), ORCHARD, "dan@example.com", "Viewer");
        send(delete("/workspaces/" + ACME + "/invitations/" + cancelled), carla, null).andExpect(status().isNoContent());
        MockHttpSession dan = as("dan");
        accept(dan, accepted).andExpect(status().isOk());
        fresh();
        mvc.perform(get("/invitations").session(dan)).andExpect(jsonPath("$", hasSize(0)));
    }

    // --- AC-INC17.13: acceptInvitation ---------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-INC17.13, AC-03.1, FR-03, FR-04 · accepting makes the caller an active member with the invited role: 200 Membership, listed in /auth/me and in the workspace's members, invitation ACCEPTED")
    void acceptCreatesTheMembership() throws Exception {
        String invitationId = inviteOk(as("carla"), ACME, "dan@example.com", "Team Member");
        MockHttpSession dan = as("dan");
        long members = count("SELECT count(*) FROM member WHERE workspace_id = ?::uuid", ACME);

        String membership = accept(dan, invitationId).andExpect(status().isOk())
                .andExpect(jsonPath("$.workspace_id").value(ACME))
                .andExpect(jsonPath("$.workspace_name").value("Acme Studio"))
                .andExpect(jsonPath("$.role").value("Team Member"))
                .andExpect(jsonPath("$.member_id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String memberId = id(membership, "$.member_id");

        assertThat(count("SELECT count(*) FROM member WHERE workspace_id = ?::uuid", ACME)).isEqualTo(members + 1);
        assertThat(statusOf(invitationId)).isEqualTo("ACCEPTED");
        assertThat(text("SELECT status FROM member WHERE member_id = ?::uuid", memberId)).isEqualTo("ACTIVE");
        assertThat(text("SELECT role FROM member WHERE member_id = ?::uuid", memberId)).isEqualTo("TEAM_MEMBER");
        mvc.perform(get("/auth/me").session(dan))
                .andExpect(jsonPath("$.memberships[?(@.workspace_id=='" + ACME + "')].member_id").value(memberId))
                .andExpect(jsonPath("$.memberships[?(@.workspace_id=='" + ACME + "')].role").value("Team Member"));
        mvc.perform(get("/workspaces/" + ACME + "/members").session(dan)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.member_id=='" + memberId + "')].name").value("Dan Demo"));
        mvc.perform(get("/workspaces/" + ACME + "/invitations").session(as("carla")))
                .andExpect(jsonPath("$", hasSize(0)));
        // Persistence: a new session of the same account reads it back.
        assertThat(membershipRole(as("dan"), ACME)).isEqualTo("Team Member");
    }

    @Test
    @DisplayName("AC-INC17.13, FR-04 · the invited role applies to the member's next request: an invited Workspace Administrator can invite, an invited Viewer cannot")
    void invitedRoleTakesEffect() throws Exception {
        String asAdmin = inviteOk(as("carla"), ACME, "dan@example.com", "Workspace Administrator");
        MockHttpSession dan = as("dan");
        accept(dan, asAdmin).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("Workspace Administrator"));
        invite(dan, ACME, body("by.dan@example.com", "Viewer")).andExpect(status().isCreated());

        String asViewer = inviteOk(as("carla"), ACME, "eva@example.com", "Viewer");
        MockHttpSession eva = as("eva");
        accept(eva, asViewer).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("Viewer"));
        invite(eva, ACME, body("by.eva@example.com", "Viewer")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-INC17.13, QK, EC-02 · accepting one invitation cancels the other pending invitations to the same address in that workspace, and leaves those of other workspaces or addresses")
    void acceptCancelsOtherPendingInvitationsToTheSameAddress() throws Exception {
        MockHttpSession carla = as("carla");
        String first = inviteOk(carla, ACME, "dan@example.com", "Team Member");
        String second = inviteOk(carla, ACME, "DAN@example.com", "Viewer");
        String otherWorkspace = inviteOk(as("finn"), ORCHARD, "dan@example.com", "Viewer");
        String otherAddress = inviteOk(carla, ACME, "not.dan@example.com", "Viewer");

        accept(as("dan"), second).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("Viewer"));
        assertThat(statusOf(second)).isEqualTo("ACCEPTED");
        assertThat(statusOf(first)).isEqualTo("CANCELLED");
        assertThat(statusOf(otherWorkspace)).isEqualTo("PENDING");
        assertThat(statusOf(otherAddress)).isEqualTo("PENDING");
        assertThat(count("SELECT count(*) FROM member m JOIN account a ON a.account_id = m.account_id "
                + "WHERE m.workspace_id = ?::uuid AND a.email = 'dan@example.com'", ACME)).isEqualTo(1);
        fresh();
        accept(as("dan"), first).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-INC17.13, Q3, API-A3 · accepting an invitation addressed to another account's email gives 404, and nothing changes")
    void otherAccountsInvitationIs404() throws Exception {
        String invitationId = inviteOk(as("carla"), ACME, "dan@example.com", "Team Member");
        long members = count("SELECT count(*) FROM member");
        MockHttpSession ana = as("ana");
        accept(ana, invitationId).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("not_found"));
        assertThat(statusOf(invitationId)).isEqualTo("PENDING");
        assertThat(count("SELECT count(*) FROM member")).isEqualTo(members);
        mvc.perform(get("/invitations").session(as("dan"))).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("AC-INC17.13, Q3 · accepting an unknown or malformed invitation id gives 404 not_found")
    void unknownInvitationIs404() throws Exception {
        MockHttpSession dan = as("dan");
        accept(dan, UUID.randomUUID().toString()).andExpect(status().isNotFound());
        accept(dan, "not-a-uuid").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-INC17.13, EC-02, BR-05 · an invitation to an account that is already an active member gives 409 conflict and creates no second member")
    void alreadyActiveMemberGets409() throws Exception {
        String invitationId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO invitation (invitation_id, workspace_id, email, role, invited_by_member_id, status) "
                + "VALUES (?::uuid, ?::uuid, 'ben@example.com', 'VIEWER', ?::uuid, 'PENDING')", invitationId, ACME,
                ACME_CARLA); // createInvitation refuses this address (EC-01); a row written before Ben joined is possible
        fresh();
        long members = count("SELECT count(*) FROM member WHERE workspace_id = ?::uuid", ACME);

        accept(as("ben"), invitationId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));
        assertThat(count("SELECT count(*) FROM member WHERE workspace_id = ?::uuid", ACME)).isEqualTo(members);
        assertThat(text("SELECT role FROM member WHERE member_id = ?::uuid", ACME_BEN)).isEqualTo("TEAM_MEMBER");
    }

    // --- AC-INC17.14: reactivation (Q8) and accepting twice -------------------------------------------------------------

    @Test
    @DisplayName("AC-INC17.14, Q8, BR-05 · accepting reactivates a deactivated membership: the same member row, active, with the invited role; no second row")
    void acceptReactivatesTheSameMemberRow() throws Exception {
        jdbc.update("UPDATE member SET status = 'DEACTIVATED' WHERE member_id = ?::uuid", ACME_BEN);
        fresh();
        String invitationId = inviteOk(as("carla"), ACME, "ben@example.com", "Project Manager");
        MockHttpSession ben = as("ben");
        assertThat(membershipRole(ben, ACME)).isNull(); // a deactivated membership is not offered

        accept(ben, invitationId).andExpect(status().isOk())
                .andExpect(jsonPath("$.member_id").value(ACME_BEN))
                .andExpect(jsonPath("$.role").value("Project Manager"));
        assertThat(text("SELECT status FROM member WHERE member_id = ?::uuid", ACME_BEN)).isEqualTo("ACTIVE");
        assertThat(text("SELECT role FROM member WHERE member_id = ?::uuid", ACME_BEN)).isEqualTo("PROJECT_MANAGER");
        assertThat(count("SELECT count(*) FROM member WHERE workspace_id = ?::uuid AND account_id = "
                + "(SELECT account_id FROM account WHERE email = 'ben@example.com')", ACME)).isEqualTo(1);
        assertThat(statusOf(invitationId)).isEqualTo("ACCEPTED");
        assertThat(membershipRole(as("ben"), ACME)).isEqualTo("Project Manager");
    }

    @Test
    @DisplayName("AC-INC17.14, EC-02 · accepting the same invitation twice: the first 200, the second 404; one membership")
    void acceptingTwiceCreatesOneMembership() throws Exception {
        String invitationId = inviteOk(as("carla"), ACME, "dan@example.com", "Viewer");
        MockHttpSession dan = as("dan");
        accept(dan, invitationId).andExpect(status().isOk());
        long members = count("SELECT count(*) FROM member WHERE workspace_id = ?::uuid", ACME);
        accept(dan, invitationId).andExpect(status().isNotFound());
        assertThat(count("SELECT count(*) FROM member WHERE workspace_id = ?::uuid", ACME)).isEqualTo(members);
    }

    @Test
    @DisplayName("AC-INC17.13 · the Viewer, Project Manager and Team Member of a workspace can accept an invitation to another workspace (any signed-in account)")
    void anySignedInAccountCanAccept() throws Exception {
        for (String who : new String[] {"vera", "ana", "ben"}) {
            String invitationId = inviteOk(as("finn"), ORCHARD, who + "@example.com", "Team Member");
            accept(as(who), invitationId).andExpect(status().isOk()).andExpect(jsonPath("$.workspace_id")
                    .value(ORCHARD));
        }
        mvc.perform(get("/invitations").session(as("vera"))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/workspaces/" + ORCHARD + "/members").session(as("vera"))).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5))); // Finn, Eva and the three who accepted
    }
}
