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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Changing roles and removing members through direct API requests (INC-18; US-04, US-05, US-26; FR-03, FR-04, FR-05,
 * FR-10, SP-02, SP-03, SP-04, SP-07, BR-04, BR-09, BR-12, EC-03, EC-04, NFR-01; openapi.yaml 0.13.0-draft; decisions
 * of 2026-11-19 Q1, Q4, QA to QG, QR). Removal replaces deactivation. Draft acceptance criteria as numbered in the
 * report of this test run: AC-INC18.2 updateMemberRole, AC-INC18.3 last administrator (409), AC-INC18.4 removal by
 * another administrator and its effects, AC-INC18.5 self-removal with new_owner_member_id, AC-INC18.6 permissions
 * and refused requests, AC-INC18.7 content of a removed member (Former member; deleting a task without creator).
 * The rename (AC-INC18.1) is in WorkspaceManageApiTest. That removal is one transaction cannot be shown inside a
 * rolled-back test; see the report.
 */
class MemberAdminApiTest extends WorkspaceAdminTestSupport {

    static final String C_BEN = "f0000000-0000-0000-0000-000000000002";

    private ResultActions changeRole(MockHttpSession s, String workspace, String member, String json) throws Exception {
        return send(patch("/workspaces/" + workspace + "/members/" + member), s, json);
    }

    private static String role(String label) {
        return "{\"role\":\"" + label + "\"}";
    }

    private ResultActions remove(MockHttpSession s, String workspace, String member, String newOwner)
            throws Exception {
        MockHttpServletRequestBuilder request = delete("/workspaces/" + workspace + "/members/" + member);
        if (newOwner != null) {
            request = request.param("new_owner_member_id", newOwner);
        }
        return send(request, s, null);
    }

    private String roleOf(String member) {
        return text("SELECT role FROM member WHERE member_id = ?::uuid", member);
    }

    private long memberRows(String member) {
        return count("SELECT count(*) FROM member WHERE member_id = ?::uuid", member);
    }

    private String taskOwner(String task) {
        return text("SELECT owner_member_id::text FROM task WHERE task_id = ?::uuid", task);
    }

    private String projectOwner(String project) {
        return text("SELECT owner_member_id::text FROM project WHERE project_id = ?::uuid", project);
    }

    private void makeAdministrator(String member) {
        jdbc.update("UPDATE member SET role = 'WORKSPACE_ADMINISTRATOR' WHERE member_id = ?::uuid", member);
        fresh();
    }

    // --- AC-INC18.2: updateMemberRole ---------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-INC18.2, FR-04, US-04 · an administrator changes a member's role: 200 WorkspaceMember with the new role, stored, and listed by listWorkspaceMembers")
    void changeRoleReturnsAndStoresTheNewRole() throws Exception {
        MockHttpSession carla = as("carla");
        changeRole(carla, ACME, ACME_BEN, role("Project Manager")).andExpect(status().isOk())
                .andExpect(jsonPath("$.member_id").value(ACME_BEN))
                .andExpect(jsonPath("$.name").value("Ben Demo"))
                .andExpect(jsonPath("$.role").value("Project Manager"));
        assertThat(roleOf(ACME_BEN)).isEqualTo("PROJECT_MANAGER");
        mvc.perform(get("/workspaces/" + ACME + "/members").session(as("vera")))
                .andExpect(jsonPath("$[?(@.member_id=='" + ACME_BEN + "')].role").value("Project Manager"));
        assertThat(membershipRole(as("ben"), ACME)).isEqualTo("Project Manager"); // new session reads it back
    }

    @Test
    @DisplayName("AC-INC18.2, FR-04 · each of the four roles can be set, including to the same role")
    void everyRoleCanBeSet() throws Exception {
        MockHttpSession carla = as("carla");
        String[][] roles = {{"Workspace Administrator", "WORKSPACE_ADMINISTRATOR"}, {"Project Manager",
            "PROJECT_MANAGER"}, {"Team Member", "TEAM_MEMBER"}, {"Viewer", "VIEWER"}, {"Viewer", "VIEWER"}};
        for (String[] r : roles) {
            changeRole(carla, ACME, ACME_VERA, role(r[0])).andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value(r[0]));
            assertThat(roleOf(ACME_VERA)).isEqualTo(r[1]);
        }
    }

    @Test
    @DisplayName("AC-INC18.2, SP-03 · the new role applies to the member's next request, on the session already open")
    void roleChangeAppliesToTheNextRequest() throws Exception {
        MockHttpSession ben = as("ben");
        String project = "{\"workspace_id\":\"" + ACME + "\",\"name\":\"Fictitious role probe\"}";
        send(post("/projects"), ben, project).andExpect(status().isForbidden()); // a Team Member cannot create projects
        changeRole(as("carla"), ACME, ACME_BEN, role("Project Manager")).andExpect(status().isOk());
        send(post("/projects"), ben, project).andExpect(status().isCreated());

        MockHttpSession ana = as("ana");
        send(post("/projects"), ana, project).andExpect(status().isCreated());
        changeRole(as("carla"), ACME, ACME_ANA, role("Viewer")).andExpect(status().isOk());
        send(post("/projects"), ana, project).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-INC18.2, FR-04 · a missing, null, empty, wrong-case, translated or unknown role gives 422 on role; the role is unchanged")
    void invalidRolesAreRefused() throws Exception {
        MockHttpSession carla = as("carla");
        String[] bodies = {"{}", "{\"role\":null}", role(""), role("team member"), role("TEAM_MEMBER"), role("viewer"),
            role("Administrador"), role("Owner"), "{\"role\":5}"};
        for (String json : bodies) {
            changeRole(carla, ACME, ACME_BEN, json).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("validation_failed"))
                    .andExpect(jsonPath("$.details[0].field").value("role"));
        }
        assertThat(roleOf(ACME_BEN)).isEqualTo("TEAM_MEMBER");
    }

    @Test
    @DisplayName("AC-INC18.2, SP-01, SP-07 · member_id, workspace_id, status, name and unknown fields give 422 on that field; an unreadable body 400; nothing changes")
    void otherFieldsAreRefused() throws Exception {
        MockHttpSession carla = as("carla");
        String base = "\"role\":\"Viewer\"";
        for (String field : new String[] {"member_id", "workspace_id", "status", "name", "account_id", "colour"}) {
            changeRole(carla, ACME, ACME_BEN, "{" + base + ",\"" + field + "\":\"x\"}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.details[0].field").value(field));
        }
        changeRole(carla, ACME, ACME_BEN, "{\"role\":").andExpect(status().isBadRequest());
        send(patch("/workspaces/" + ACME + "/members/" + ACME_BEN), carla, null).andExpect(status().isBadRequest());
        assertThat(roleOf(ACME_BEN)).isEqualTo("TEAM_MEMBER");
        assertThat(text("SELECT status FROM member WHERE member_id = ?::uuid", ACME_BEN)).isEqualTo("ACTIVE");
    }

    // --- AC-INC18.3: the last administrator -------------------------------------------------------------------------

    @Test
    @DisplayName("AC-INC18.3, EC-03, SP-07, BR-04 · the only active Workspace Administrator cannot change their own role: 409 conflict on role, role unchanged")
    void onlyAdministratorCannotDemoteThemselves() throws Exception {
        MockHttpSession carla = as("carla");
        for (String label : new String[] {"Project Manager", "Team Member", "Viewer"}) {
            changeRole(carla, ACME, ACME_CARLA, role(label)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("conflict"))
                    .andExpect(jsonPath("$.details[0].field").value("role"));
        }
        assertThat(roleOf(ACME_CARLA)).isEqualTo("WORKSPACE_ADMINISTRATOR");
        changeRole(carla, ACME, ACME_CARLA, role("Workspace Administrator")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC-INC18.3, QG, SP-07 · with two administrators, one may demote the other and themselves; the last one left cannot be demoted, also by themselves")
    void administratorsMayDemoteEachOtherButNotTheLastOne() throws Exception {
        makeAdministrator(ACME_BEN);
        MockHttpSession carla = as("carla");
        MockHttpSession ben = as("ben");

        changeRole(ben, ACME, ACME_CARLA, role("Project Manager")).andExpect(status().isOk());
        assertThat(roleOf(ACME_CARLA)).isEqualTo("PROJECT_MANAGER");
        // Carla's open session now has the new role: she no longer manages the workspace (SP-03).
        changeRole(carla, ACME, ACME_BEN, role("Viewer")).andExpect(status().isForbidden());
        // Ben is the last one left.
        changeRole(ben, ACME, ACME_BEN, role("Team Member")).andExpect(status().isConflict());
        assertThat(roleOf(ACME_BEN)).isEqualTo("WORKSPACE_ADMINISTRATOR");
    }

    @Test
    @DisplayName("AC-INC18.3, QG · an administrator may demote themselves when another administrator remains")
    void administratorMayDemoteThemselvesWhenAnotherRemains() throws Exception {
        makeAdministrator(ACME_BEN);
        changeRole(as("carla"), ACME, ACME_CARLA, role("Viewer")).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("Viewer"));
        assertThat(roleOf(ACME_CARLA)).isEqualTo("VIEWER");
    }

    @Test
    @DisplayName("AC-INC18.3, SP-04, SP-07 · a deactivated administrator does not count: the only active one cannot demote themselves (409)")
    void deactivatedAdministratorDoesNotCount() throws Exception {
        makeAdministrator(ACME_BEN);
        jdbc.update("UPDATE member SET status = 'DEACTIVATED' WHERE member_id = ?::uuid", ACME_BEN);
        fresh();
        changeRole(as("carla"), ACME, ACME_CARLA, role("Viewer")).andExpect(status().isConflict());
        assertThat(roleOf(ACME_CARLA)).isEqualTo("WORKSPACE_ADMINISTRATOR");
    }

    // --- AC-INC18.6: permissions, separation, authentication ------------------------------------------------------------

    @Test
    @DisplayName("AC-INC18.6, Q4, FR-05, SP-03 · a Project Manager, a Team Member and a Viewer get 403 on changing a role (also their own) and on removing a member (also themselves); nothing changes")
    void otherRolesAreForbidden() throws Exception {
        for (String who : new String[] {"ana", "ben", "vera"}) {
            MockHttpSession s = as(who);
            changeRole(s, ACME, ACME_VERA, role("Workspace Administrator")).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("forbidden"));
            changeRole(s, ACME, ACME_ANA, role("Workspace Administrator")).andExpect(status().isForbidden());
            remove(s, ACME, ACME_VERA, null).andExpect(status().isForbidden());
            remove(s, ACME, ACME_BEN, ACME_CARLA).andExpect(status().isForbidden());
        }
        assertThat(roleOf(ACME_VERA)).isEqualTo("VIEWER");
        assertThat(roleOf(ACME_ANA)).isEqualTo("PROJECT_MANAGER");
        assertThat(count("SELECT count(*) FROM member WHERE workspace_id = ?::uuid", ACME)).isEqualTo(4);
    }

    @Test
    @DisplayName("AC-INC18.6, NFR-01, SP-02 · another workspace's member or administrator gets 404 on changing a role and removing a member; an unknown or malformed workspace or member id is 404; nothing changes")
    void otherWorkspaceAndUnknownIdsAre404() throws Exception {
        for (String who : new String[] {"dan", "finn"}) {
            MockHttpSession s = as(who);
            changeRole(s, ACME, ACME_BEN, role("Viewer")).andExpect(status().isNotFound());
            remove(s, ACME, ACME_BEN, null).andExpect(status().isNotFound());
        }
        MockHttpSession carla = as("carla");
        String unknown = UUID.randomUUID().toString();
        changeRole(carla, unknown, ACME_BEN, role("Viewer")).andExpect(status().isNotFound());
        changeRole(carla, "not-a-uuid", ACME_BEN, role("Viewer")).andExpect(status().isNotFound());
        changeRole(carla, ACME, unknown, role("Viewer")).andExpect(status().isNotFound());
        changeRole(carla, ACME, "not-a-uuid", role("Viewer")).andExpect(status().isNotFound());
        remove(carla, unknown, ACME_BEN, null).andExpect(status().isNotFound());
        remove(carla, ACME, unknown, null).andExpect(status().isNotFound());
        remove(carla, ACME, "not-a-uuid", null).andExpect(status().isNotFound());
        assertThat(roleOf(ACME_BEN)).isEqualTo("TEAM_MEMBER");
        assertThat(count("SELECT count(*) FROM member WHERE workspace_id = ?::uuid", ACME)).isEqualTo(4);
    }

    @Test
    @DisplayName("AC-INC18.6, NFR-01, BR-05 · a member of another workspace reached through this workspace's id gives 404 on role change and removal, even for an administrator of both; they stay unchanged")
    void memberThroughAnotherWorkspaceIs404() throws Exception {
        MockHttpSession carla = as("carla"); // administrator of Acme Studio and Northwind Lab
        changeRole(carla, ACME, NW_DAN, role("Viewer")).andExpect(status().isNotFound());
        remove(carla, ACME, NW_DAN, null).andExpect(status().isNotFound());
        changeRole(carla, NORTHWIND, ACME_BEN, role("Viewer")).andExpect(status().isNotFound());
        remove(carla, NORTHWIND, ACME_BEN, null).andExpect(status().isNotFound());
        assertThat(roleOf(NW_DAN)).isEqualTo("PROJECT_MANAGER");
        assertThat(roleOf(ACME_BEN)).isEqualTo("TEAM_MEMBER");
        assertThat(memberRows(NW_DAN)).isEqualTo(1);
        assertThat(memberRows(ACME_BEN)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-INC18.6, SP-04 · a deactivated administrator gets 403 on changing a role and removing a member")
    void deactivatedAdministratorIsForbidden() throws Exception {
        MockHttpSession carla = as("carla");
        jdbc.update("UPDATE member SET status = 'DEACTIVATED' WHERE member_id = ?::uuid", ACME_CARLA);
        fresh();
        changeRole(carla, ACME, ACME_BEN, role("Viewer")).andExpect(status().isForbidden());
        remove(carla, ACME, ACME_BEN, null).andExpect(status().isForbidden());
        assertThat(roleOf(ACME_BEN)).isEqualTo("TEAM_MEMBER");
        assertThat(memberRows(ACME_BEN)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-INC18.6, SP-01 · no session gives 401, no CSRF token gives 403 csrf_invalid; nothing changes")
    void authenticationAndCsrf() throws Exception {
        mvc.perform(patch("/workspaces/" + ACME + "/members/" + ACME_BEN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(role("Viewer")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("not_authenticated"));
        mvc.perform(delete("/workspaces/" + ACME + "/members/" + ACME_BEN).with(csrf()))
                .andExpect(status().isUnauthorized());
        MockHttpSession carla = as("carla");
        mvc.perform(patch("/workspaces/" + ACME + "/members/" + ACME_BEN).session(carla)
                        .contentType(MediaType.APPLICATION_JSON).content(role("Viewer")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("csrf_invalid"));
        mvc.perform(delete("/workspaces/" + ACME + "/members/" + ACME_BEN).session(carla))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("csrf_invalid"));
        assertThat(roleOf(ACME_BEN)).isEqualTo("TEAM_MEMBER");
        assertThat(memberRows(ACME_BEN)).isEqualTo(1);
    }

    // --- AC-INC18.4: removal by another administrator and its effects --------------------------------------------------

    /** Ben (Team Member) owns three tasks and, in the data below, a project; he has assignments, notifications, an invitation and feedback. */
    private void giveBenSomethingOfEverything() {
        jdbc.update("UPDATE project SET owner_member_id = ?::uuid WHERE project_id = ?::uuid", ACME_BEN, TRADE_FAIR);
        jdbc.update("INSERT INTO task_assignee (task_id, member_id) VALUES (?::uuid, ?::uuid)", T_LAYOUT, ACME_BEN);
        jdbc.update("INSERT INTO task_assignee (task_id, member_id) VALUES (?::uuid, ?::uuid)", T_IMAGES, ACME_BEN);
        jdbc.update("INSERT INTO notification (recipient_member_id, type, task_id) "
                + "VALUES (?::uuid, 'TASK_ASSIGNED', ?::uuid)", ACME_BEN, T_IMAGES);
        jdbc.update("INSERT INTO invitation (workspace_id, email, role, invited_by_member_id, status) "
                + "VALUES (?::uuid, 'invited.by.ben@example.com', 'VIEWER', ?::uuid, 'PENDING')", ACME, ACME_BEN);
        jdbc.update("INSERT INTO feedback (member_id, message) VALUES (?::uuid, 'Fictitious feedback')", ACME_BEN);
        fresh();
    }

    @Test
    @DisplayName("AC-INC18.4, QA, FR-03 · removing a member deletes the member row permanently (204); the account stays, the member leaves the workspace's members and /auth/me")
    void removalDeletesTheMemberButKeepsTheAccount() throws Exception {
        long accounts = count("SELECT count(*) FROM account");
        long members = count("SELECT count(*) FROM member WHERE workspace_id = ?::uuid", ACME);

        remove(as("carla"), ACME, ACME_BEN, null).andExpect(status().isNoContent());

        assertThat(memberRows(ACME_BEN)).isZero();
        assertThat(count("SELECT count(*) FROM member WHERE workspace_id = ?::uuid", ACME)).isEqualTo(members - 1);
        assertThat(count("SELECT count(*) FROM account")).isEqualTo(accounts);
        assertThat(count("SELECT count(*) FROM account WHERE email = 'ben@example.com'")).isEqualTo(1);
        fresh();
        mvc.perform(get("/workspaces/" + ACME + "/members").session(as("vera")))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.member_id=='" + ACME_BEN + "')]", hasSize(0)));
        assertThat(membershipRole(as("ben"), ACME)).isNull();
        // The removed person no longer reaches the workspace at all: 404, not 403 (no membership, NFR-01).
        mvc.perform(get("/workspaces/" + ACME + "/members").session(as("ben"))).andExpect(status().isNotFound());
        mvc.perform(get("/projects/" + WEBSITE).session(as("ben"))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-INC18.4, QB, QC, FR-10, BR-09 · tasks and projects the removed member owns go to the removing administrator, who joins those projects; the tasks stay in their projects")
    void ownedTasksAndProjectsGoToTheRemovingAdministrator() throws Exception {
        giveBenSomethingOfEverything();
        long tasks = count("SELECT count(*) FROM task");
        long projects = count("SELECT count(*) FROM project");
        assertThat(count("SELECT count(*) FROM project_member WHERE member_id = ?::uuid", ACME_CARLA)).isZero();

        remove(as("carla"), ACME, ACME_BEN, null).andExpect(status().isNoContent());

        assertThat(taskOwner(T_IMAGES)).isEqualTo(ACME_CARLA);
        assertThat(taskOwner(T_PUBLISH)).isEqualTo(ACME_CARLA);
        assertThat(taskOwner(T_KICKOFF)).isEqualTo(ACME_CARLA);
        assertThat(projectOwner(TRADE_FAIR)).isEqualTo(ACME_CARLA);
        assertThat(projectOwner(WEBSITE)).isEqualTo(ACME_ANA); // not owned by Ben: unchanged
        assertThat(taskOwner(T_LAYOUT)).isEqualTo(ACME_ANA);
        assertThat(count("SELECT count(*) FROM project_member WHERE member_id = ?::uuid AND project_id IN (?::uuid, ?::uuid)",
                ACME_CARLA, WEBSITE, TRADE_FAIR)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM task")).isEqualTo(tasks);
        assertThat(count("SELECT count(*) FROM project")).isEqualTo(projects);
        assertThat(text("SELECT project_id::text FROM task WHERE task_id = ?::uuid", T_KICKOFF)).isEqualTo(TRADE_FAIR);

        fresh();
        // The new owner can be given the task's work as a project member: tasks list shows the new owner's name.
        mvc.perform(get("/projects/" + WEBSITE + "/tasks").session(as("carla"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.task_id=='" + T_IMAGES + "')].owner_name").value("Carla Demo"));
    }

    @Test
    @DisplayName("AC-INC18.4, QD, QF · the removed member's assignments, project memberships and notifications are deleted; other members' rows are kept")
    void membersOwnRecordsAreDeleted() throws Exception {
        giveBenSomethingOfEverything();
        jdbc.update("INSERT INTO task_assignee (task_id, member_id) VALUES (?::uuid, ?::uuid)", T_LAYOUT, ACME_VERA);
        long assigneesOfOthers = count("SELECT count(*) FROM task_assignee WHERE member_id <> ?::uuid", ACME_BEN);
        long projectMembersOfOthers = count("SELECT count(*) FROM project_member WHERE member_id <> ?::uuid",
                ACME_BEN);
        assertThat(count("SELECT count(*) FROM task_assignee WHERE member_id = ?::uuid", ACME_BEN)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM project_member WHERE member_id = ?::uuid", ACME_BEN)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM notification WHERE recipient_member_id = ?::uuid", ACME_BEN))
                .isEqualTo(1);

        remove(as("carla"), ACME, ACME_BEN, null).andExpect(status().isNoContent());

        assertThat(count("SELECT count(*) FROM task_assignee WHERE member_id = ?::uuid", ACME_BEN)).isZero();
        assertThat(count("SELECT count(*) FROM project_member WHERE member_id = ?::uuid", ACME_BEN)).isZero();
        assertThat(count("SELECT count(*) FROM notification WHERE recipient_member_id = ?::uuid", ACME_BEN)).isZero();
        assertThat(count("SELECT count(*) FROM task_assignee WHERE member_id <> ?::uuid", ACME_BEN))
                .isEqualTo(assigneesOfOthers);
        // Carla joined two projects as the new owner; everyone else's project memberships are unchanged.
        assertThat(count("SELECT count(*) FROM project_member WHERE member_id <> ?::uuid", ACME_BEN))
                .isEqualTo(projectMembersOfOthers + 2);
    }

    @Test
    @DisplayName("AC-INC18.4, QR, QD · what the removed member created is kept with no creator or author: tasks, comments, invitations and feedback rows stay, their member reference is empty")
    void createdContentIsKeptWithoutAMember() throws Exception {
        giveBenSomethingOfEverything();
        long tasks = count("SELECT count(*) FROM task");
        long comments = count("SELECT count(*) FROM comment");
        long invitations = count("SELECT count(*) FROM invitation");
        long feedback = count("SELECT count(*) FROM feedback");

        remove(as("carla"), ACME, ACME_BEN, null).andExpect(status().isNoContent());

        assertThat(count("SELECT count(*) FROM task")).isEqualTo(tasks);
        assertThat(count("SELECT count(*) FROM comment")).isEqualTo(comments);
        assertThat(count("SELECT count(*) FROM invitation")).isEqualTo(invitations);
        assertThat(count("SELECT count(*) FROM feedback")).isEqualTo(feedback);
        assertThat(count("SELECT count(*) FROM task WHERE task_id IN (?::uuid, ?::uuid) AND created_by_member_id IS NULL",
                T_PUBLISH, T_KICKOFF)).isEqualTo(2);
        assertThat(text("SELECT created_by_member_id::text FROM task WHERE task_id = ?::uuid", T_IMAGES))
                .isEqualTo(ACME_ANA); // created by Ana: unchanged
        assertThat(count("SELECT count(*) FROM comment WHERE comment_id = ?::uuid AND author_member_id IS NULL", C_BEN))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM invitation WHERE email = 'invited.by.ben@example.com' "
                + "AND invited_by_member_id IS NULL AND status = 'PENDING'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM feedback WHERE message = 'Fictitious feedback' AND member_id IS NULL"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("AC-INC18.7, QR, FR-16 · after the removal comments by the removed member read 200 with author_name Former member; the other comments keep their author; invitations by them show Former member")
    void removedAuthorIsShownAsFormerMember() throws Exception {
        giveBenSomethingOfEverything();
        remove(as("carla"), ACME, ACME_BEN, null).andExpect(status().isNoContent());
        fresh();

        mvc.perform(get("/projects/" + WEBSITE + "/tasks/" + T_IMAGES + "/comments").session(as("vera")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.comment_id=='" + C_BEN + "')].author_name").value("Former member"))
                .andExpect(jsonPath("$[?(@.comment_id=='f0000000-0000-0000-0000-000000000001')].author_name")
                        .value("Ana Demo"));
        mvc.perform(get("/workspaces/" + ACME + "/invitations").session(as("carla")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='invited.by.ben@example.com')].invited_by_name")
                        .value("Former member"));
        // Tasks created by the removed member still open and list.
        mvc.perform(get("/projects/" + WEBSITE + "/tasks/" + T_PUBLISH).session(as("vera")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.task_id").value(T_PUBLISH));
        mvc.perform(get("/projects/" + WEBSITE + "/tasks").session(as("vera"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC-INC18.7, QE, BR-12, FR-05 · a task whose creator was removed can be deleted by a Workspace Administrator and a Project Manager, not by a Team Member or a Viewer; one with a creator still only by that creator")
    void taskWithoutCreatorCanBeDeletedByAdministratorsAndProjectManagers() throws Exception {
        giveBenSomethingOfEverything();
        remove(as("carla"), ACME, ACME_BEN, null).andExpect(status().isNoContent());
        jdbc.update("UPDATE member SET role = 'TEAM_MEMBER' WHERE member_id = ?::uuid", ACME_VERA);
        fresh();

        // No creator: a Viewer and a Team Member are refused, nothing is deleted.
        MockHttpSession vera = as("vera");
        send(delete("/projects/" + WEBSITE + "/tasks/" + T_PUBLISH), vera, null).andExpect(status().isForbidden());
        assertThat(count("SELECT count(*) FROM task WHERE task_id = ?::uuid", T_PUBLISH)).isEqualTo(1);
        jdbc.update("UPDATE member SET role = 'VIEWER' WHERE member_id = ?::uuid", ACME_VERA);
        fresh();
        send(delete("/projects/" + WEBSITE + "/tasks/" + T_PUBLISH), as("vera"), null).andExpect(status().isForbidden());
        assertThat(count("SELECT count(*) FROM task WHERE task_id = ?::uuid", T_PUBLISH)).isEqualTo(1);

        // A creator exists (Ana created Approve page layout): the administrator who is not the creator is still refused.
        send(delete("/projects/" + WEBSITE + "/tasks/" + T_LAYOUT), as("carla"), null).andExpect(status().isForbidden());
        assertThat(count("SELECT count(*) FROM task WHERE task_id = ?::uuid", T_LAYOUT)).isEqualTo(1);

        // No creator: the administrator and the Project Manager may delete.
        send(delete("/projects/" + WEBSITE + "/tasks/" + T_PUBLISH), as("carla"), null)
                .andExpect(status().is2xxSuccessful());
        send(delete("/projects/" + TRADE_FAIR + "/tasks/" + T_KICKOFF), as("ana"), null)
                .andExpect(status().is2xxSuccessful());
        assertThat(count("SELECT count(*) FROM task WHERE task_id IN (?::uuid, ?::uuid)", T_PUBLISH, T_KICKOFF))
                .isZero();
    }

    @Test
    @DisplayName("AC-INC18.4, QA · a deactivated member can be removed too; other members and their roles are untouched")
    void deactivatedMemberCanBeRemoved() throws Exception {
        jdbc.update("UPDATE member SET status = 'DEACTIVATED' WHERE member_id = ?::uuid", ACME_VERA);
        fresh();
        remove(as("carla"), ACME, ACME_VERA, null).andExpect(status().isNoContent());
        assertThat(memberRows(ACME_VERA)).isZero();
        assertThat(roleOf(ACME_BEN)).isEqualTo("TEAM_MEMBER");
        assertThat(roleOf(ACME_ANA)).isEqualTo("PROJECT_MANAGER");
    }

    @Test
    @DisplayName("AC-INC18.4, QG, SP-07 · an administrator removes another administrator when one remains; the last administrator cannot be removed (409) and nothing changes")
    void administratorCanBeRemovedUnlessLast() throws Exception {
        makeAdministrator(ACME_BEN);
        MockHttpSession carla = as("carla");
        MockHttpSession ben = as("ben");
        remove(ben, ACME, ACME_CARLA, null).andExpect(status().isNoContent());
        assertThat(memberRows(ACME_CARLA)).isZero();
        // Carla is gone; Ben is the last administrator.
        remove(ben, ACME, ACME_BEN, ACME_BEN).andExpect(status().isConflict()).andExpect(jsonPath("$.code")
                .value("conflict"));
        assertThat(memberRows(ACME_BEN)).isEqualTo(1);
        remove(carla, ACME, ACME_BEN, null).andExpect(status().isNotFound()); // Carla has no membership any more
    }

    @Test
    @DisplayName("AC-INC18.3, EC-03, SP-07, BR-04 · the only administrator cannot be removed by themselves (409), with or without a new owner; they stay a member")
    void onlyAdministratorCannotRemoveThemselves() throws Exception {
        MockHttpSession carla = as("carla");
        remove(carla, ACME, ACME_CARLA, ACME_BEN).andExpect(status().isConflict());
        assertThat(memberRows(ACME_CARLA)).isEqualTo(1);
        assertThat(roleOf(ACME_CARLA)).isEqualTo("WORKSPACE_ADMINISTRATOR");
        assertThat(count("SELECT count(*) FROM member WHERE workspace_id = ?::uuid", ACME)).isEqualTo(4);
    }

    @Test
    @DisplayName("AC-INC18.5, QC, 422 · new_owner_member_id cannot be sent when removing someone else; nothing changes")
    void newOwnerNotAllowedForSomeoneElse() throws Exception {
        MockHttpSession carla = as("carla");
        remove(carla, ACME, ACME_BEN, ACME_CARLA).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.details[0].field").value("new_owner_member_id"));
        remove(carla, ACME, ACME_BEN, "not-a-uuid").andExpect(status().isUnprocessableEntity());
        assertThat(memberRows(ACME_BEN)).isEqualTo(1);
        assertThat(taskOwner(T_IMAGES)).isEqualTo(ACME_BEN);
    }

    // --- AC-INC18.5: self-removal ---------------------------------------------------------------------------------------

    private void giveCarlaSomethingToOwn() {
        jdbc.update("INSERT INTO project_member (project_id, member_id) VALUES (?::uuid, ?::uuid)", WEBSITE, ACME_CARLA);
        jdbc.update("UPDATE task SET owner_member_id = ?::uuid WHERE task_id = ?::uuid", ACME_CARLA, T_LAYOUT);
        jdbc.update("UPDATE project SET owner_member_id = ?::uuid WHERE project_id = ?::uuid", ACME_CARLA, WEBSITE);
        fresh();
    }

    @Test
    @DisplayName("AC-INC18.5, QC, QG · an administrator removes themselves, naming another active administrator: 204; their tasks and projects go to that administrator, who joins the projects")
    void selfRemovalTransfersToTheChosenAdministrator() throws Exception {
        giveCarlaSomethingToOwn();
        makeAdministrator(ACME_BEN);
        jdbc.update("DELETE FROM project_member WHERE member_id = ?::uuid AND project_id = ?::uuid", ACME_BEN, WEBSITE);
        fresh(); // Ben is no longer in Website redesign; he must join it as the new owner

        remove(as("carla"), ACME, ACME_CARLA, ACME_BEN).andExpect(status().isNoContent());

        assertThat(memberRows(ACME_CARLA)).isZero();
        assertThat(taskOwner(T_LAYOUT)).isEqualTo(ACME_BEN);
        assertThat(projectOwner(WEBSITE)).isEqualTo(ACME_BEN);
        assertThat(count("SELECT count(*) FROM project_member WHERE member_id = ?::uuid AND project_id = ?::uuid",
                ACME_BEN, WEBSITE)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM project_member WHERE member_id = ?::uuid", ACME_CARLA)).isZero();
        assertThat(roleOf(ACME_BEN)).isEqualTo("WORKSPACE_ADMINISTRATOR");
        // Carla keeps her account and her other workspace; Acme gives her 404 from now on.
        MockHttpSession carla = as("carla");
        assertThat(membershipRole(carla, ACME)).isNull();
        assertThat(membershipRole(carla, NORTHWIND)).isEqualTo("Workspace Administrator");
        mvc.perform(get("/workspaces/" + ACME + "/members").session(carla)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-INC18.5, QC · self-removal without new_owner_member_id gives 422 on new_owner_member_id; nothing changes")
    void selfRemovalNeedsANewOwner() throws Exception {
        makeAdministrator(ACME_BEN);
        remove(as("carla"), ACME, ACME_CARLA, null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.details[0].field").value("new_owner_member_id"));
        assertThat(memberRows(ACME_CARLA)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-INC18.5, QC · self-removal with a new owner who is not another active administrator of this workspace gives 422: yourself, a Project Manager, a Viewer, a deactivated administrator, an administrator of another workspace, an unknown or malformed id")
    void selfRemovalNewOwnerMustBeAnotherActiveAdministrator() throws Exception {
        makeAdministrator(ACME_BEN);
        makeAdministrator(ACME_ANA); // Ana: a third administrator, deactivated below
        jdbc.update("UPDATE member SET status = 'DEACTIVATED' WHERE member_id = ?::uuid", ACME_ANA);
        fresh();
        MockHttpSession carla = as("carla");
        for (String newOwner : new String[] {ACME_CARLA, ACME_VERA, ACME_ANA, NW_CARLA, UUID.randomUUID().toString(),
            "not-a-uuid"}) {
            remove(carla, ACME, ACME_CARLA, newOwner).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.details[0].field").value("new_owner_member_id"));
        }
        jdbc.update("UPDATE member SET role = 'PROJECT_MANAGER', status = 'ACTIVE' WHERE member_id = ?::uuid", ACME_ANA);
        fresh();
        remove(carla, ACME, ACME_CARLA, ACME_ANA).andExpect(status().isUnprocessableEntity());
        assertThat(memberRows(ACME_CARLA)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM member WHERE workspace_id = ?::uuid", ACME)).isEqualTo(4);
    }

}
