package com.matriz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Renaming and deleting a workspace through direct API requests (INC-18 rename, INC-19 delete; US-02; FR-02, FR-23,
 * FR-24, BR-01, SP-01, SP-02, SP-04, NFR-01; openapi.yaml 0.13.0-draft; decisions of 2026-11-19 Q1, Q4, Q13, QN, QO).
 * Draft acceptance criteria as numbered in the report of this test run: AC-INC18.1 updateWorkspace; AC-INC19.1
 * deleteWorkspace (confirm name, soft delete); AC-INC19.2 a deleted workspace gives 404 everywhere and leaves
 * /auth/me, listMyInvitations and acceptInvitation; AC-INC19.3 permissions and refused requests; AC-INC19.4 other
 * workspaces are not affected. The mail sender is not needed here. Invitations are written with SQL.
 */
class WorkspaceManageApiTest extends WorkspaceAdminTestSupport {

    private ResultActions rename(MockHttpSession s, String workspace, String json) throws Exception {
        return send(patch("/workspaces/" + workspace), s, json);
    }

    private ResultActions deleteWorkspace(MockHttpSession s, String workspace, String json) throws Exception {
        return send(delete("/workspaces/" + workspace), s, json);
    }

    private static String named(String name) {
        return "{\"name\":\"" + name + "\"}";
    }

    private static String confirm(String name) {
        return "{\"confirm_name\":\"" + name + "\"}";
    }

    private String nameOf(String workspace) {
        return text("SELECT name FROM workspace WHERE workspace_id = ?::uuid", workspace);
    }

    private boolean deleted(String workspace) {
        return count("SELECT count(*) FROM workspace WHERE workspace_id = ?::uuid AND deleted_at IS NOT NULL",
                workspace) == 1;
    }

    private void assertRenameRefused(MockHttpSession s, String json, String field) throws Exception {
        rename(s, ACME, json).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.details[0].field").value(field));
        assertThat(nameOf(ACME)).as("unchanged after " + json).isEqualTo("Acme Studio");
    }

    private void assertDeleteRefused(MockHttpSession s, String json, String field) throws Exception {
        deleteWorkspace(s, ACME, json).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.details[0].field").value(field));
        assertThat(deleted(ACME)).as("not deleted after " + json).isFalse();
    }

    // --- AC-INC18.1: updateWorkspace ---------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-INC18.1, FR-23, BR-01 · an administrator renames the workspace: 200 WorkspaceSummary with the trimmed name, stored, seen by every member in /auth/me, other workspaces unchanged")
    void renameReturnsAndStoresTheName() throws Exception {
        rename(as("carla"), ACME, named("  Acme Renamed  ")).andExpect(status().isOk())
                .andExpect(jsonPath("$.workspace_id").value(ACME))
                .andExpect(jsonPath("$.name").value("Acme Renamed"));
        assertThat(nameOf(ACME)).isEqualTo("Acme Renamed");
        assertThat(nameOf(NORTHWIND)).isEqualTo("Northwind Lab");
        for (String who : new String[] {"ben", "vera"}) {
            mvc.perform(get("/auth/me").session(as(who)))
                    .andExpect(jsonPath("$.memberships[?(@.workspace_id=='" + ACME + "')].workspace_name")
                            .value("Acme Renamed"));
        }
    }

    @Test
    @DisplayName("AC-INC18.1, FR-23, BR-01 · name boundaries: 2 characters refused, 3 and 100 accepted, 101 refused (422 on name)")
    void renameLengthBoundaries() throws Exception {
        MockHttpSession carla = as("carla");
        assertRenameRefused(carla, named("ab"), "name");
        assertRenameRefused(carla, named("  ab  "), "name");
        rename(carla, ACME, named("abc")).andExpect(status().isOk());
        rename(carla, ACME, named("n".repeat(100))).andExpect(status().isOk());
        rename(carla, ACME, named("  " + "m".repeat(100) + "  ")).andExpect(status().isOk());
        assertThat(nameOf(ACME)).isEqualTo("m".repeat(100));
        jdbc.update("UPDATE workspace SET name = 'Acme Studio' WHERE workspace_id = ?::uuid", ACME);
        fresh();
        assertRenameRefused(carla, named("n".repeat(101)), "name");
    }

    @Test
    @DisplayName("AC-INC18.1, BR-01 · whitespace-only, empty, null, missing and NUL-containing names give 422 on name; nothing changes")
    void renameInvalidNamesAreRefused() throws Exception {
        MockHttpSession carla = as("carla");
        assertRenameRefused(carla, named("   "), "name");
        assertRenameRefused(carla, named(""), "name");
        assertRenameRefused(carla, "{\"name\":null}", "name");
        assertRenameRefused(carla, "{}", "name");
        assertRenameRefused(carla, "{\"name\":\"ab\\u0000cd\"}", "name");
        assertRenameRefused(carla, "{\"name\":\"\\u0000abc\"}", "name");
    }

    @Test
    @DisplayName("AC-INC18.1, BR-01 · a duplicate name is allowed, also the name of another workspace; a JSON number is stored as text")
    void renameDuplicatesAllowed() throws Exception {
        MockHttpSession carla = as("carla");
        rename(carla, ACME, named("Northwind Lab")).andExpect(status().isOk());
        rename(carla, ACME, named("Northwind Lab")).andExpect(status().isOk()); // the same name again
        assertThat(count("SELECT count(*) FROM workspace WHERE name = 'Northwind Lab'")).isEqualTo(2);
        rename(carla, ACME, "{\"name\":12345}").andExpect(status().isOk()).andExpect(jsonPath("$.name").value("12345"));
    }

    @Test
    @DisplayName("AC-INC18.1, SP-01, SP-07 · workspace_id, role, deleted_at, member_id and unknown fields give 422 on that field; an unreadable body 400; nothing changes")
    void renameOtherFieldsAreRefused() throws Exception {
        MockHttpSession carla = as("carla");
        for (String field : new String[] {"workspace_id", "role", "deleted_at", "member_id", "colour"}) {
            assertRenameRefused(carla, "{\"name\":\"Valid name\",\"" + field + "\":\"x\"}", field);
        }
        rename(carla, ACME, "{\"name\":").andExpect(status().isBadRequest());
        send(patch("/workspaces/" + ACME), carla, null).andExpect(status().isBadRequest());
        assertThat(nameOf(ACME)).isEqualTo("Acme Studio");
    }

    @Test
    @DisplayName("AC-INC18.1, Q1, Q4, FR-05 · a Project Manager, a Team Member and a Viewer get 403 on rename; any other active Workspace Administrator may rename; the name is unchanged after a refusal")
    void renamePermissions() throws Exception {
        for (String who : new String[] {"ana", "ben", "vera"}) {
            rename(as(who), ACME, named("Hijacked " + who)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("forbidden"));
        }
        assertThat(nameOf(ACME)).isEqualTo("Acme Studio");
        MockHttpSession ben = as("ben");
        jdbc.update("UPDATE member SET role = 'WORKSPACE_ADMINISTRATOR' WHERE member_id = ?::uuid", ACME_BEN);
        fresh();
        rename(ben, ACME, named("Renamed by Ben")).andExpect(status().isOk());
        assertThat(nameOf(ACME)).isEqualTo("Renamed by Ben");
    }

    @Test
    @DisplayName("AC-INC18.1, NFR-01, SP-02, SP-04 · another workspace's member or administrator and an unknown or malformed id get 404; a deactivated administrator gets 403; nothing changes")
    void renameSeparationAndDeactivation() throws Exception {
        for (String who : new String[] {"dan", "finn"}) {
            rename(as(who), ACME, named("Hijacked " + who)).andExpect(status().isNotFound());
        }
        MockHttpSession carla = as("carla");
        rename(carla, UUID.randomUUID().toString(), named("Valid name")).andExpect(status().isNotFound());
        rename(carla, "not-a-uuid", named("Valid name")).andExpect(status().isNotFound());
        jdbc.update("UPDATE member SET status = 'DEACTIVATED' WHERE member_id = ?::uuid", ACME_CARLA);
        fresh();
        rename(carla, ACME, named("Valid name")).andExpect(status().isForbidden());
        assertThat(nameOf(ACME)).isEqualTo("Acme Studio");
    }

    @Test
    @DisplayName("AC-INC18.1, SP-01 · rename and delete without a session give 401; without a CSRF token 403 csrf_invalid; nothing changes")
    void renameAndDeleteNeedSessionAndCsrf() throws Exception {
        mvc.perform(patch("/workspaces/" + ACME).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(named("Anonymous")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("not_authenticated"));
        mvc.perform(delete("/workspaces/" + ACME).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(confirm("Acme Studio")))
                .andExpect(status().isUnauthorized());
        MockHttpSession carla = as("carla");
        mvc.perform(patch("/workspaces/" + ACME).session(carla).contentType(MediaType.APPLICATION_JSON)
                        .content(named("No token")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("csrf_invalid"));
        mvc.perform(delete("/workspaces/" + ACME).session(carla).contentType(MediaType.APPLICATION_JSON)
                        .content(confirm("Acme Studio")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("csrf_invalid"));
        assertThat(nameOf(ACME)).isEqualTo("Acme Studio");
        assertThat(deleted(ACME)).isFalse();
    }

    // --- AC-INC19.1: deleteWorkspace -----------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-INC19.1, FR-24, QN, QO · typing the current name deletes the workspace softly: 204, the row and all its data are kept, deleted_at is set")
    void deleteIsSoft() throws Exception {
        long members = rowsOf("member", ACME);
        long projects = rowsOf("project", ACME);
        long invitations = rowsOf("invitation", ACME);
        long tasks = count("SELECT count(*) FROM task");
        long comments = count("SELECT count(*) FROM comment");
        long accounts = count("SELECT count(*) FROM account");

        deleteWorkspace(as("carla"), ACME, confirm("Acme Studio")).andExpect(status().isNoContent());

        assertThat(deleted(ACME)).isTrue();
        assertThat(nameOf(ACME)).isEqualTo("Acme Studio");
        assertThat(rowsOf("member", ACME)).isEqualTo(members);
        assertThat(rowsOf("project", ACME)).isEqualTo(projects);
        assertThat(rowsOf("invitation", ACME)).isEqualTo(invitations);
        assertThat(count("SELECT count(*) FROM task")).isEqualTo(tasks);
        assertThat(count("SELECT count(*) FROM comment")).isEqualTo(comments);
        assertThat(count("SELECT count(*) FROM account")).isEqualTo(accounts);
        assertThat(count("SELECT count(*) FROM member WHERE workspace_id = ?::uuid AND status = 'ACTIVE'", ACME))
                .isEqualTo(members);
    }

    @Test
    @DisplayName("AC-INC19.1, QN · the typed name is trimmed and compared with letter case: padded spaces accepted; other case, inner spaces, a different name, empty, spaces-only, null and missing give 422 on confirm_name; nothing deleted")
    void confirmNameRules() throws Exception {
        MockHttpSession carla = as("carla");
        for (String typed : new String[] {"acme studio", "ACME STUDIO", "Acme  Studio", "Acme", "Acme Studio X",
            "Northwind Lab", "", "   "}) {
            assertDeleteRefused(carla, confirm(typed), "confirm_name");
        }
        assertDeleteRefused(carla, "{\"confirm_name\":null}", "confirm_name");
        assertDeleteRefused(carla, "{}", "confirm_name");
        deleteWorkspace(carla, ACME, confirm("  Acme Studio \\t")).andExpect(status().isNoContent());
        assertThat(deleted(ACME)).isTrue();
    }

    @Test
    @DisplayName("AC-INC19.1, QN, FR-23 · the name that counts is the current one: after a rename the old name is refused and the new one accepted")
    void confirmNameIsTheCurrentName() throws Exception {
        MockHttpSession carla = as("carla");
        rename(carla, ACME, named("Acme Renamed")).andExpect(status().isOk());
        assertDeleteRefused(carla, confirm("Acme Studio"), "confirm_name");
        deleteWorkspace(carla, ACME, confirm("Acme Renamed")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("AC-INC19.1, SP-01 · unknown fields give 422 on that field; an unreadable body or no body gives 400; nothing is deleted")
    void deleteBodyRules() throws Exception {
        MockHttpSession carla = as("carla");
        for (String field : new String[] {"name", "workspace_id", "deleted_at", "force"}) {
            assertDeleteRefused(carla, "{\"confirm_name\":\"Acme Studio\",\"" + field + "\":\"x\"}", field);
        }
        deleteWorkspace(carla, ACME, "{\"confirm_name\":").andExpect(status().isBadRequest());
        deleteWorkspace(carla, ACME, null).andExpect(status().isBadRequest());
        assertThat(deleted(ACME)).isFalse();
    }

    // --- AC-INC19.2: a deleted workspace is gone ------------------------------------------------------------------------

    @Test
    @DisplayName("AC-INC19.2, FR-24, Q13, NFR-01 · after the delete every former member gets 404 on the workspace, its members, invitations, projects, tasks and comments; the second delete is 404; it leaves /auth/me and the project list")
    void deletedWorkspaceIsNotFoundEverywhere() throws Exception {
        MockHttpSession carla = as("carla");
        MockHttpSession ana = as("ana"); // opened before the delete
        MockHttpSession vera = as("vera");
        deleteWorkspace(carla, ACME, confirm("Acme Studio")).andExpect(status().isNoContent());
        fresh();

        for (MockHttpSession s : new MockHttpSession[] {carla, ana, vera, as("ben")}) {
            mvc.perform(get("/workspaces/" + ACME + "/members").session(s)).andExpect(status().isNotFound());
            mvc.perform(get("/projects/" + WEBSITE).session(s)).andExpect(status().isNotFound());
            mvc.perform(get("/projects/" + WEBSITE + "/tasks").session(s)).andExpect(status().isNotFound());
            mvc.perform(get("/projects/" + WEBSITE + "/tasks/" + T_IMAGES).session(s)).andExpect(status().isNotFound());
            mvc.perform(get("/projects/" + WEBSITE + "/tasks/" + T_IMAGES + "/comments").session(s))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/auth/me").session(s)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.memberships[?(@.workspace_id=='" + ACME + "')]", hasSize(0)));
            mvc.perform(get("/projects").session(s)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[?(@.project_id=='" + WEBSITE + "')]", hasSize(0)))
                    .andExpect(jsonPath("$.items[?(@.project_id=='" + TRADE_FAIR + "')]", hasSize(0)));
        }
        // Carla, the administrator, is refused every management operation too, with 404 (not 403).
        mvc.perform(get("/workspaces/" + ACME + "/invitations").session(carla)).andExpect(status().isNotFound());
        send(post("/workspaces/" + ACME + "/invitations"), carla, "{\"email\":\"late@example.com\",\"role\":\"Viewer\"}")
                .andExpect(status().isNotFound());
        rename(carla, ACME, named("Back again")).andExpect(status().isNotFound());
        deleteWorkspace(carla, ACME, confirm("Acme Studio")).andExpect(status().isNotFound());
        send(patch("/workspaces/" + ACME + "/members/" + ACME_BEN), carla, "{\"role\":\"Viewer\"}")
                .andExpect(status().isNotFound());
        send(delete("/workspaces/" + ACME + "/members/" + ACME_BEN), carla, null).andExpect(status().isNotFound());
        send(post("/projects"), carla, "{\"workspace_id\":\"" + ACME + "\",\"name\":\"Fictitious late project\"}")
                .andExpect(status().isNotFound());
        send(post("/projects/" + WEBSITE + "/tasks/" + T_IMAGES + "/comments"), ana, "{\"body\":\"Fictitious late note\"}")
                .andExpect(status().isNotFound());
        send(delete("/projects/" + WEBSITE + "/tasks/" + T_LAYOUT), ana, null).andExpect(status().isNotFound());
        assertThat(nameOf(ACME)).isEqualTo("Acme Studio");
        assertThat(count("SELECT count(*) FROM task WHERE task_id = ?::uuid", T_LAYOUT)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM comment WHERE task_id = ?::uuid", T_IMAGES)).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-INC19.2, PD-24 in part, QM, Q3 · invitations of a deleted workspace leave listMyInvitations and give 404 on accept; the invitation row is kept PENDING")
    void deletedWorkspaceInvitations() throws Exception {
        String invitationId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO invitation (invitation_id, workspace_id, email, role, invited_by_member_id, status) "
                + "VALUES (?::uuid, ?::uuid, 'dan@example.com', 'VIEWER', ?::uuid, 'PENDING')", invitationId, ACME,
                ACME_CARLA);
        fresh();
        MockHttpSession dan = as("dan");
        mvc.perform(get("/invitations").session(dan)).andExpect(jsonPath("$", hasSize(1)));

        deleteWorkspace(as("carla"), ACME, confirm("Acme Studio")).andExpect(status().isNoContent());
        fresh();

        mvc.perform(get("/invitations").session(dan)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        send(post("/invitations/" + invitationId + "/accept"), dan, null).andExpect(status().isNotFound());
        assertThat(text("SELECT status FROM invitation WHERE invitation_id = ?::uuid", invitationId))
                .isEqualTo("PENDING");
        assertThat(count("SELECT count(*) FROM member WHERE workspace_id = ?::uuid AND account_id = "
                + "(SELECT account_id FROM account WHERE email = 'dan@example.com')", ACME)).isZero();
    }

    // --- AC-INC19.3: permissions ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-INC19.3, Q4, FR-05 · a Project Manager, a Team Member and a Viewer get 403 on delete, even with the right name; nothing is deleted")
    void deleteIsForbiddenForOtherRoles() throws Exception {
        for (String who : new String[] {"ana", "ben", "vera"}) {
            deleteWorkspace(as(who), ACME, confirm("Acme Studio")).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("forbidden"));
        }
        assertThat(deleted(ACME)).isFalse();
        mvc.perform(get("/workspaces/" + ACME + "/members").session(as("vera"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC-INC19.3, NFR-01, SP-02, SP-04 · another workspace's member or administrator and an unknown or malformed id get 404; a deactivated administrator gets 403; nothing is deleted")
    void deleteSeparationAndDeactivation() throws Exception {
        for (String who : new String[] {"dan", "finn"}) {
            deleteWorkspace(as(who), ACME, confirm("Acme Studio")).andExpect(status().isNotFound());
        }
        MockHttpSession carla = as("carla");
        deleteWorkspace(carla, UUID.randomUUID().toString(), confirm("Acme Studio")).andExpect(status().isNotFound());
        deleteWorkspace(carla, "not-a-uuid", confirm("Acme Studio")).andExpect(status().isNotFound());
        jdbc.update("UPDATE member SET status = 'DEACTIVATED' WHERE member_id = ?::uuid", ACME_CARLA);
        fresh();
        deleteWorkspace(carla, ACME, confirm("Acme Studio")).andExpect(status().isForbidden());
        assertThat(deleted(ACME)).isFalse();
    }

    @Test
    @DisplayName("AC-INC19.3, Q1 · any active Workspace Administrator may delete, also one promoted after sign-in, not only the creator")
    void secondAdministratorMayDelete() throws Exception {
        MockHttpSession ben = as("ben");
        deleteWorkspace(ben, ACME, confirm("Acme Studio")).andExpect(status().isForbidden());
        jdbc.update("UPDATE member SET role = 'WORKSPACE_ADMINISTRATOR' WHERE member_id = ?::uuid", ACME_BEN);
        fresh();
        deleteWorkspace(ben, ACME, confirm("Acme Studio")).andExpect(status().isNoContent());
        assertThat(deleted(ACME)).isTrue();
    }

    // --- AC-INC19.4: other workspaces are not affected -------------------------------------------------------------------

    @Test
    @DisplayName("AC-INC19.4, NFR-01, BR-18 · deleting one workspace leaves the administrator's other workspace, its data and its members working; a new workspace with the same name can be created")
    void otherWorkspacesAreNotAffected() throws Exception {
        MockHttpSession carla = as("carla"); // administrator of Acme Studio and Northwind Lab
        deleteWorkspace(carla, ACME, confirm("Acme Studio")).andExpect(status().isNoContent());
        fresh();

        assertThat(deleted(NORTHWIND)).isFalse();
        assertThat(membershipRole(carla, NORTHWIND)).isEqualTo("Workspace Administrator");
        assertThat(membershipRole(carla, ACME)).isNull();
        mvc.perform(get("/workspaces/" + NORTHWIND + "/members").session(carla)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
        mvc.perform(get("/projects/" + LAB).session(carla)).andExpect(status().isOk());
        mvc.perform(get("/projects/" + LAB + "/tasks/" + T_EQUIPMENT).session(as("ana"))).andExpect(status().isOk());
        mvc.perform(get("/projects").session(carla)).andExpect(jsonPath("$.items[?(@.project_id=='" + LAB + "')]",
                hasSize(1)));
        send(post("/workspaces"), carla, named("Acme Studio")).andExpect(status().isCreated());
        assertThat(count("SELECT count(*) FROM workspace WHERE name = 'Acme Studio' AND deleted_at IS NULL"))
                .isEqualTo(1);
        // Ana still belongs to Northwind Lab; Acme has left her /auth/me.
        MockHttpSession ana = as("ana");
        assertThat(membershipRole(ana, ACME)).isNull();
        assertThat(membershipRole(ana, NORTHWIND)).isEqualTo("Team Member");
    }
}
