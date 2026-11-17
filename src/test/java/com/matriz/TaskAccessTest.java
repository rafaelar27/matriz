package com.matriz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

/**
 * INC-08, US-10, US-11: authorization of createTask and of status changes through updateTask
 * (PATCH /projects/{project_id}/tasks/{task_id}, body {"status": ...}) by direct API requests (access rules table).
 * The unscoped PATCH /tasks/{id} no longer exists; one regression test checks that.
 */
class TaskAccessTest extends ApiTestSupport {

    @PersistenceContext
    private EntityManager entityManager;

    private ResultActions createTask(MockHttpSession session, String projectId, String json) throws Exception {
        return mvc.perform(post("/projects/" + projectId + "/tasks").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Status change through the project-scoped updateTask operation. */
    private ResultActions changeStatus(MockHttpSession session, String projectId, String taskId, String json)
            throws Exception {
        return mvc.perform(patch("/projects/" + projectId + "/tasks/" + taskId).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** The stored row, read from the database (pending writes flushed, then the persistence context emptied). */
    private Map<String, Object> row(String taskId) {
        entityManager.flush();
        entityManager.clear();
        return jdbc.queryForMap("SELECT status, title, owner_member_id::text AS owner FROM task WHERE task_id = ?::uuid",
                taskId);
    }

    /** Number of stored tasks (pending writes flushed first). */
    private long taskCount() {
        entityManager.flush();
        return jdbc.queryForObject("SELECT count(*) FROM task", Long.class);
    }

    private static final String NW_ANA = "c0000000-0000-0000-0000-000000000006";

    private static String task(String title, String owner) {
        return "{\"title\":\"" + title + "\",\"owner_member_id\":\"" + owner + "\"}";
    }

    // --- createTask: allowed cases -------------------------------------------------------------------------------

    @Test
    void projectManagerCreatesATaskForAProjectMember() throws Exception {
        createTask(signIn("ana@example.com"), WEBSITE,
                "{\"title\":\"Check page speed\",\"owner_member_id\":\"" + ACME_BEN + "\",\"due_date\":\"2026-11-25\"}")
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("To do"))
                .andExpect(jsonPath("$.owner_member_id").value(ACME_BEN))
                .andExpect(jsonPath("$.created_by_member_id").value(ACME_ANA));
    }

    @Test
    void aViewerWhoIsAProjectMemberCanBeTheOwner() throws Exception {
        createTask(signIn("ana@example.com"), WEBSITE, task("Read the brief", ACME_VERA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.owner_member_id").value(ACME_VERA));
    }

    @Test
    void teamMemberCreatesATaskTheyOwn() throws Exception {
        createTask(signIn("ben@example.com"), TRADE_FAIR, task("Book the stand", ACME_BEN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.owner_member_id").value(ACME_BEN))
                .andExpect(jsonPath("$.created_by_member_id").value(ACME_BEN));
    }

    // --- Identity comes only from the session ---------------------------------------------------------------------

    @Test
    void clientSuppliedCreatorIsRefused() throws Exception {
        createTask(signIn("ben@example.com"), TRADE_FAIR, "{\"title\":\"Spoof\",\"owner_member_id\":\"" + ACME_BEN
                        + "\",\"created_by_member_id\":\"" + ACME_ANA + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("created_by_member_id"));
    }

    @Test
    void statusIsOptionalOnCreateButMustBeValid() throws Exception {
        // Contract 0.4.0 (decided 2026-11-12): status may be sent; To do is the default.
        createTask(signIn("ana@example.com"), WEBSITE,
                "{\"title\":\"Placeholder task\",\"owner_member_id\":\"" + ACME_ANA + "\",\"status\":\"Done\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("Done"));
        createTask(signIn("ana@example.com"), WEBSITE,
                "{\"title\":\"Placeholder task\",\"owner_member_id\":\"" + ACME_ANA + "\",\"status\":\"DONE\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("status"));
    }

    @Test
    void creatorIsTheSignedInMemberEvenWhenTheOwnerIsSomeoneElse() throws Exception {
        createTask(signIn("ana@example.com"), WEBSITE, task("Assign to Ben", ACME_BEN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created_by_member_id").value(ACME_ANA));
    }

    // --- Eligible owners -----------------------------------------------------------------------------------------

    @Test
    void ownerWhoIsNotAProjectMemberIsRefused() throws Exception {
        // Vera is a member of Acme Studio but not of "Trade fair stand" (AC-10.3).
        createTask(signIn("ana@example.com"), TRADE_FAIR, task("Placeholder task", ACME_VERA))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("owner_member_id"));
    }

    @Test
    void ownerFromAnotherWorkspaceIsRefused() throws Exception {
        createTask(signIn("ana@example.com"), WEBSITE, task("Placeholder task", NW_CARLA))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("owner_member_id"));
    }

    @Test
    void missingOwnerOrTitleIsRefused() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        createTask(ana, WEBSITE, "{\"title\":\"x\"}").andExpect(status().isUnprocessableEntity());
        createTask(ana, WEBSITE, "{\"title\":\"\",\"owner_member_id\":\"" + ACME_ANA + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("title"));
    }

    // --- Roles ---------------------------------------------------------------------------------------------------

    @Test
    void viewerCannotCreateTasks() throws Exception {
        createTask(signIn("vera@example.com"), WEBSITE, task("Placeholder task", ACME_VERA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Viewers have read-only access."));
    }

    @Test
    void workspaceAdministratorCannotCreateTasksUntilPd03() throws Exception {
        long tasks = taskCount();
        createTask(signIn("carla@example.com"), LAB, task("Placeholder task", NW_CARLA))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
        assertThat(taskCount()).isEqualTo(tasks);
    }

    @Test
    void teamMemberCannotAssignSomeoneElseUntilPd01() throws Exception {
        long tasks = taskCount();
        createTask(signIn("ben@example.com"), WEBSITE, task("Placeholder task", ACME_ANA))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
        assertThat(taskCount()).isEqualTo(tasks);
    }

    @Test
    void creatorMustBeAProjectMemberUntilPd20() throws Exception {
        jdbc.update("DELETE FROM project_member WHERE project_id = ?::uuid AND member_id = ?::uuid", TRADE_FAIR, ACME_BEN);
        entityManager.clear();
        long tasks = taskCount();
        createTask(signIn("ben@example.com"), TRADE_FAIR, task("Placeholder task", ACME_BEN))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
        assertThat(taskCount()).isEqualTo(tasks);
    }

    @Test
    @DisplayName("AC-11.1, SP-04 · a member deactivated after sign-in is refused on the next request, status unchanged")
    void deactivatedMemberIsRefused() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        Map<String, Object> before = row(T_IMAGES);
        jdbc.update("UPDATE member SET status = 'DEACTIVATED' WHERE member_id = ?::uuid", ACME_BEN);
        // The test shares one persistence context across requests; a real request starts with an empty one.
        entityManager.clear();
        long tasks = taskCount();
        // Takes effect on the next request, without signing out (SP-04).
        createTask(ben, TRADE_FAIR, task("Placeholder task", ACME_BEN)).andExpect(status().isForbidden());
        assertThat(taskCount()).as("TC-08: nothing created").isEqualTo(tasks);
        // Q-03 is open: only "refused (not 200) and unchanged" is asserted, not a specific 403 or 404.
        int refused = changeStatus(ben, WEBSITE, T_IMAGES, "{\"status\":\"Done\"}").andReturn().getResponse().getStatus();
        assertThat(refused).isNotEqualTo(200);
        assertThat(row(T_IMAGES)).isEqualTo(before);
    }

    // --- Workspace isolation (NFR-01): another workspace looks like nothing exists --------------------------------

    @Test
    void projectInAnotherWorkspaceIsNotFound() throws Exception {
        createTask(signIn("ben@example.com"), LAB, task("Placeholder task", ACME_BEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    @DisplayName("AC-11.1, NFR-01 · a status change on a task of another workspace, or through another project id, is 404 not_found and changes nothing")
    void taskInAnotherWorkspaceIsNotFound() throws Exception {
        Map<String, Object> before = row(T_EQUIPMENT);
        // Vera (Acme) aims at a Northwind task, through its own project and through an Acme project.
        MockHttpSession vera = signIn("vera@example.com");
        changeStatus(vera, LAB, T_EQUIPMENT, "{\"status\":\"Done\"}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("not_found"));
        changeStatus(vera, WEBSITE, T_EQUIPMENT, "{\"status\":\"Done\"}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("not_found"));
        // An Acme task through another Acme project id (the task is not in that project).
        Map<String, Object> images = row(T_IMAGES);
        changeStatus(signIn("ben@example.com"), TRADE_FAIR, T_IMAGES, "{\"status\":\"Done\"}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("not_found"));
        assertThat(row(T_EQUIPMENT)).isEqualTo(before);
        assertThat(row(T_IMAGES)).isEqualTo(images);
    }

    @Test
    void unknownAndMalformedIdsAreNotFound() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        createTask(ana, "d0000000-0000-0000-0000-000000000999", task("Placeholder task", ACME_ANA)).andExpect(status().isNotFound());
        changeStatus(ana, WEBSITE, "not-an-id", "{\"status\":\"Done\"}").andExpect(status().isNotFound());
        changeStatus(ana, WEBSITE, "e0000000-0000-0000-0000-000000000999", "{\"status\":\"Done\"}")
                .andExpect(status().isNotFound());
    }

    // --- Status changes through updateTask (AC-11.1 to AC-11.3) ---------------------------------------------------

    @Test
    @DisplayName("AC-11.1, AC-11.2 · the owner sets To do, In progress and Done through updateTask and gets the task back")
    void ownerChangesTheStatusThroughUpdateTask() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        for (String value : new String[] {"Done", "To do", "In progress"}) {
            changeStatus(ben, WEBSITE, T_IMAGES, "{\"status\":\"" + value + "\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.task_id").value(T_IMAGES))
                    .andExpect(jsonPath("$.status").value(value));
        }
        assertThat(row(T_IMAGES).get("status")).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("AC-11.1, FR-04, FR-05 · a non-owner Project Manager (PD-01 provisional) and a Workspace Administrator "
            + "(PD-03 provisional) get 403 forbidden and the status is unchanged")
    void nonOwnerProjectManagerAndAdministratorCannotChangeTheStatusUntilPd01AndPd03() throws Exception {
        Map<String, Object> images = row(T_IMAGES);
        changeStatus(signIn("ana@example.com"), WEBSITE, T_IMAGES, "{\"status\":\"Done\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
        assertThat(row(T_IMAGES)).isEqualTo(images);
        // Carla is the Workspace Administrator of Northwind Lab; the rule is provisional (PD-03).
        Map<String, Object> equipment = row(T_EQUIPMENT);
        changeStatus(signIn("carla@example.com"), LAB, T_EQUIPMENT, "{\"status\":\"Done\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
        assertThat(row(T_EQUIPMENT)).isEqualTo(equipment);
    }

    @Test
    @DisplayName("AC-11.1, FR-04, PD-17 · a Viewer, even when the task owner (PD-17 provisional), gets 403 and the status is unchanged")
    void viewerCannotChangeTheStatus() throws Exception {
        MockHttpSession vera = signIn("vera@example.com");
        Map<String, Object> before = row(T_LAYOUT);
        changeStatus(vera, WEBSITE, T_LAYOUT, "{\"status\":\"Done\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
        assertThat(row(T_LAYOUT)).isEqualTo(before);
        // Vera is made the owner of the task (she is a project member); a Viewer stays read-only.
        jdbc.update("UPDATE task SET owner_member_id = ?::uuid WHERE task_id = ?::uuid", ACME_VERA, T_LAYOUT);
        Map<String, Object> owned = row(T_LAYOUT);
        changeStatus(vera, WEBSITE, T_LAYOUT, "{\"status\":\"Done\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
        assertThat(row(T_LAYOUT)).isEqualTo(owned);
    }

    @Test
    @DisplayName("AC-11.3, EC-11 · invalid status values are 422 validation_failed on status; {} changes nothing; "
            + "an owner change by a Team Member is 403; unknown fields are 422; non-JSON is 400; missing CSRF is 403")
    void invalidOrExtraFieldsAreRefused() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        Map<String, Object> before = row(T_IMAGES);
        for (String bad : new String[] {"\"Blocked\"", "\"done\"", "\"DONE\"", "\"\"", "\"   \"", "null"}) {
            changeStatus(ben, WEBSITE, T_IMAGES, "{\"status\":" + bad + "}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("validation_failed"))
                    .andExpect(jsonPath("$.details[0].field").value("status"));
            assertThat(row(T_IMAGES)).as("after status " + bad).isEqualTo(before);
        }
        // An empty body is a valid update with nothing to change (TaskUpdateRequest has no required field).
        changeStatus(ben, WEBSITE, T_IMAGES, "{}").andExpect(status().isOk());
        assertThat(row(T_IMAGES)).isEqualTo(before);
        // Permissions are checked before values: a Team Member may not set the owner (PD-01 provisional).
        changeStatus(ben, WEBSITE, T_IMAGES, "{\"status\":\"Done\",\"owner_member_id\":\"" + ACME_ANA + "\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
        assertThat(row(T_IMAGES)).isEqualTo(before);
        // The creator comes from the session; unknown fields are refused.
        changeStatus(ben, WEBSITE, T_IMAGES, "{\"status\":\"Done\",\"created_by_member_id\":\"" + ACME_BEN + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.details[0].field").value("created_by_member_id"));
        assertThat(row(T_IMAGES)).isEqualTo(before);
        changeStatus(ben, WEBSITE, T_IMAGES, "not json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed_request"));
        assertThat(row(T_IMAGES)).isEqualTo(before);
        mvc.perform(patch("/projects/" + WEBSITE + "/tasks/" + T_IMAGES).session(ben)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"Done\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("csrf_invalid"));
        assertThat(row(T_IMAGES)).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-11.1, INC-08 · regression: the unscoped PATCH /tasks/{id} no longer changes a task")
    void unscopedStatusRouteIsGone() throws Exception {
        Map<String, Object> before = row(T_IMAGES);
        int result = mvc.perform(patch("/tasks/" + T_IMAGES).session(signIn("ben@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"Done\"}"))
                .andReturn().getResponse().getStatus();
        assertThat(result / 100).isNotEqualTo(2);
        assertThat(row(T_IMAGES)).isEqualTo(before);
    }

    // --- New scenarios (test-plan matrix) -------------------------------------------------------------------------

    @Test
    @DisplayName("TV-05 · FR-10, BR-09, AC-10.3 · a missing owner (valid title) or a nonexistent owner id is 422 on owner_member_id and nothing is created")
    void missingOrNonexistentOwnerIsRefused() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        long tasks = taskCount();
        createTask(ana, WEBSITE, "{\"title\":\"A valid title\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.details[0].field").value("owner_member_id"));
        assertThat(taskCount()).isEqualTo(tasks);
        createTask(ana, WEBSITE, task("A valid title", "c0000000-0000-0000-0000-000000000999"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.details[0].field").value("owner_member_id"));
        assertThat(taskCount()).isEqualTo(tasks);
    }

    @Test
    @DisplayName("TC-07 (e) · PD-20 prov. · a Project Manager who is not a member of the project cannot create in it: 403 forbidden, nothing created")
    void projectManagerOutsideTheProjectCannotCreateUntilPd20() throws Exception {
        // Dan is a Project Manager of Northwind Lab, not a member of Lab inventory; the owner is a project member.
        long tasks = taskCount();
        createTask(signIn("dan@example.com"), LAB, task("Placeholder task", NW_ANA))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
        assertThat(taskCount()).isEqualTo(tasks);
    }

    @Test
    @DisplayName("TU-07 (a) · updateTask · permissions are checked before values: a non-owner Project Manager sending a valid title and a status gets 403, not 422, and the title is unchanged")
    void permissionIsCheckedBeforeValuesForANonOwnerProjectManager() throws Exception {
        Map<String, Object> before = row(T_IMAGES);
        changeStatus(signIn("ana@example.com"), WEBSITE, T_IMAGES, "{\"title\":\"New title\",\"status\":\"Done\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
        assertThat(row(T_IMAGES)).isEqualTo(before);
    }

    @Test
    @DisplayName("TU-07 (b) · updateTask, PD-01 prov. · permissions are checked before values: a Team Member sending a new owner and an empty title gets 403, not 422, and nothing changes")
    void permissionIsCheckedBeforeValuesForATeamMemberSettingTheOwner() throws Exception {
        Map<String, Object> before = row(T_IMAGES);
        changeStatus(signIn("ben@example.com"), WEBSITE, T_IMAGES,
                "{\"owner_member_id\":\"" + ACME_ANA + "\",\"title\":\"\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
        assertThat(row(T_IMAGES)).isEqualTo(before);
    }

    @Test
    @DisplayName("TA-07 · SP-04, FR-03, Q-03 open · a member deactivated after sign-in cannot list or read tasks: not 2xx, 403 or 404, no task data")
    void deactivatedMemberCannotReadTasks() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        jdbc.update("UPDATE member SET status = 'DEACTIVATED' WHERE member_id = ?::uuid", ACME_BEN);
        entityManager.clear();
        var list = mvc.perform(get("/projects/" + WEBSITE + "/tasks").session(ben)).andReturn().getResponse();
        var one = mvc.perform(get("/projects/" + WEBSITE + "/tasks/" + T_IMAGES).session(ben)).andReturn().getResponse();
        for (var response : new org.springframework.mock.web.MockHttpServletResponse[] {list, one}) {
            // Q-03 is open: 403 or 404 are both accepted; the exact code is not asserted.
            assertThat(response.getStatus()).isIn(403, 404);
            String body = response.getContentAsString();
            assertThat(body).doesNotContain("\"items\"").doesNotContain("\"title\"")
                    .doesNotContain("Review homepage images");
        }
    }
}
