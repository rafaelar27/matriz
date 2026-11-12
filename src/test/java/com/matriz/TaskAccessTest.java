package com.matriz;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

/** Authorization of createTask and updateTaskStatus through direct API requests (access rules table). */
class TaskAccessTest extends ApiTestSupport {

    @PersistenceContext
    private EntityManager entityManager;

    private ResultActions createTask(MockHttpSession session, String projectId, String json) throws Exception {
        return mvc.perform(post("/projects/" + projectId + "/tasks").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions changeStatus(MockHttpSession session, String taskId, String json) throws Exception {
        return mvc.perform(patch("/tasks/" + taskId).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

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
    void clientSuppliedStatusIsRefused() throws Exception {
        createTask(signIn("ana@example.com"), WEBSITE,
                "{\"title\":\"x\",\"owner_member_id\":\"" + ACME_ANA + "\",\"status\":\"Done\"}")
                .andExpect(status().isUnprocessableEntity());
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
        createTask(signIn("ana@example.com"), TRADE_FAIR, task("x", ACME_VERA))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("owner_member_id"));
    }

    @Test
    void ownerFromAnotherWorkspaceIsRefused() throws Exception {
        createTask(signIn("ana@example.com"), WEBSITE, task("x", NW_CARLA))
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
        createTask(signIn("vera@example.com"), WEBSITE, task("x", ACME_VERA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Viewers have read-only access."));
    }

    @Test
    void workspaceAdministratorCannotCreateTasksUntilPd03() throws Exception {
        createTask(signIn("carla@example.com"), LAB, task("x", NW_CARLA))
                .andExpect(status().isForbidden());
    }

    @Test
    void teamMemberCannotAssignSomeoneElseUntilPd01() throws Exception {
        createTask(signIn("ben@example.com"), WEBSITE, task("x", ACME_ANA))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatorMustBeAProjectMemberUntilPd20() throws Exception {
        jdbc.update("DELETE FROM project_member WHERE project_id = ?::uuid AND member_id = ?::uuid", TRADE_FAIR, ACME_BEN);
        createTask(signIn("ben@example.com"), TRADE_FAIR, task("x", ACME_BEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivatedMemberIsRefused() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        jdbc.update("UPDATE member SET status = 'DEACTIVATED' WHERE member_id = ?::uuid", ACME_BEN);
        // The test shares one persistence context across requests; a real request starts with an empty one.
        entityManager.clear();
        // Takes effect on the next request, without signing out (SP-04).
        createTask(ben, TRADE_FAIR, task("x", ACME_BEN)).andExpect(status().isForbidden());
        changeStatus(ben, T_IMAGES, "{\"status\":\"Done\"}").andExpect(status().isForbidden());
    }

    // --- Workspace isolation (NFR-01): another workspace looks like nothing exists --------------------------------

    @Test
    void projectInAnotherWorkspaceIsNotFound() throws Exception {
        createTask(signIn("ben@example.com"), LAB, task("x", ACME_BEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void taskInAnotherWorkspaceIsNotFound() throws Exception {
        changeStatus(signIn("vera@example.com"), T_EQUIPMENT, "{\"status\":\"Done\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownAndMalformedIdsAreNotFound() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        createTask(ana, "d0000000-0000-0000-0000-000000000999", task("x", ACME_ANA)).andExpect(status().isNotFound());
        changeStatus(ana, "not-an-id", "{\"status\":\"Done\"}").andExpect(status().isNotFound());
    }

    // --- updateTaskStatus ----------------------------------------------------------------------------------------

    @Test
    void ownerChangesTheStatus() throws Exception {
        changeStatus(signIn("ben@example.com"), T_IMAGES, "{\"status\":\"Done\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Done"));
    }

    @Test
    void nonOwnerProjectManagerCannotChangeTheStatusUntilPd01() throws Exception {
        changeStatus(signIn("ana@example.com"), T_IMAGES, "{\"status\":\"Done\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void viewerCannotChangeTheStatus() throws Exception {
        changeStatus(signIn("vera@example.com"), T_LAYOUT, "{\"status\":\"Done\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidOrExtraFieldsAreRefused() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        changeStatus(ben, T_IMAGES, "{\"status\":\"Blocked\"}").andExpect(status().isUnprocessableEntity());
        changeStatus(ben, T_IMAGES, "{}").andExpect(status().isUnprocessableEntity());
        changeStatus(ben, T_IMAGES, "{\"status\":\"Done\",\"owner_member_id\":\"" + ACME_BEN + "\"}")
                .andExpect(status().isUnprocessableEntity());
        changeStatus(ben, T_IMAGES, "not json").andExpect(status().isBadRequest());
    }
}
