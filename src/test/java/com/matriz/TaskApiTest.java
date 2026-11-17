package com.matriz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.jayway.jsonpath.JsonPath;

/**
 * Task operations through direct API requests (INC-04; openapi.yaml 0.4.0-draft; FR-09 to FR-13, FR-15; US-10,
 * US-11, US-12, US-14, US-25, US-26). Demo data: "Website redesign" has Review homepage images (Ben, In progress,
 * created by Ana), Approve page layout (Ana, To do, created by Ana) and Publish new homepage (Ben, Done, created by
 * Ben); project members Ana, Ben and Vera.
 */
class TaskApiTest extends ApiTestSupport {

    static final String T_PUBLISH = "e0000000-0000-0000-0000-000000000003";
    static final String T_KICKOFF = "e0000000-0000-0000-0000-000000000004";
    static final String ACME_CARLA = "c0000000-0000-0000-0000-000000000001";

    @PersistenceContext
    private EntityManager entityManager;

    private static String path(String project, String task) {
        return "/projects/" + project + "/tasks/" + task;
    }

    private ResultActions create(MockHttpSession s, String project, String json) throws Exception {
        return mvc.perform(post("/projects/" + project + "/tasks").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions update(MockHttpSession s, String project, String task, String json) throws Exception {
        return mvc.perform(patch(path(project, task)).session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions remove(MockHttpSession s, String project, String task) throws Exception {
        return mvc.perform(delete(path(project, task)).session(s).with(csrf()));
    }

    private ResultActions list(MockHttpSession s, String project, String... params) throws Exception {
        var request = get("/projects/" + project + "/tasks").session(s);
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request);
    }

    private static String titled(String title) {
        return "{\"title\":\"" + title + "\",\"owner_member_id\":\"" + ACME_ANA + "\"}";
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    // --- Create: defaults and validation --------------------------------------------------------------------------

    @Test
    void createWithoutStatusUsesTheDefaultAndAppearsInTheList() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        String body = create(ana, WEBSITE, "{\"title\":\"  Check page speed  \",\"owner_member_id\":\"" + ACME_BEN
                + "\",\"due_date\":\"2026-11-25\"}")
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.title").value("Check page speed"))
                .andExpect(jsonPath("$.status").value("To do"))
                .andExpect(jsonPath("$.owner_member_id").value(ACME_BEN))
                .andExpect(jsonPath("$.owner_name").value("Ben Demo"))
                .andExpect(jsonPath("$.created_by_member_id").value(ACME_ANA))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.task_id");

        mvc.perform(get(path(WEBSITE, id)).session(ana)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("To do"));
        list(ana, WEBSITE).andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.status_counts[0].status").value("To do"))
                .andExpect(jsonPath("$.status_counts[0].count").value(2));
    }

    @Test
    void titleLengthIsCheckedOnTheTrimmedValue() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        create(ana, WEBSITE, titled("ab")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.details[0].field").value("title"));
        create(ana, WEBSITE, titled("abc")).andExpect(status().isCreated());
        create(ana, WEBSITE, titled("a".repeat(150))).andExpect(status().isCreated());
        create(ana, WEBSITE, titled("a".repeat(151))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("title"));
        create(ana, WEBSITE, titled("   ")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].issue").value("A title is required."));
        create(ana, WEBSITE, titled("  ab  ")).andExpect(status().isUnprocessableEntity());
        create(ana, WEBSITE, titled("   " + "a".repeat(150) + "   ")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("a".repeat(150)));
        create(ana, WEBSITE, "{\"owner_member_id\":\"" + ACME_ANA + "\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("title"));
    }

    @Test
    void descriptionIsAtMost2000Characters() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        create(ana, WEBSITE, "{\"title\":\"Long text\",\"owner_member_id\":\"" + ACME_ANA + "\",\"description\":\""
                + "d".repeat(2000) + "\"}").andExpect(status().isCreated());
        create(ana, WEBSITE, "{\"title\":\"Long text\",\"owner_member_id\":\"" + ACME_ANA + "\",\"description\":\""
                + "d".repeat(2001) + "\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("description"));
        update(ana, WEBSITE, T_LAYOUT, "{\"description\":\"" + "d".repeat(2000) + "\"}").andExpect(status().isOk());
        update(ana, WEBSITE, T_LAYOUT, "{\"description\":\"" + "d".repeat(2001) + "\"}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void statusMustBeOneOfTheThreeApiValues() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        create(ana, WEBSITE, "{\"title\":\"Started already\",\"owner_member_id\":\"" + ACME_ANA
                + "\",\"status\":\"In progress\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("In progress"));
        for (String bad : new String[] {"DONE", "done", "Concluída", "Blocked", ""}) {
            create(ana, WEBSITE, "{\"title\":\"Bad status\",\"owner_member_id\":\"" + ACME_ANA + "\",\"status\":\""
                    + bad + "\"}").andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.details[0].field").value("status"));
            update(ana, WEBSITE, T_LAYOUT, "{\"status\":\"" + bad + "\"}").andExpect(status().isUnprocessableEntity());
        }
        update(ana, WEBSITE, T_LAYOUT, "{\"status\":null}").andExpect(status().isUnprocessableEntity());
        list(ana, WEBSITE, "status", "TODO").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("status"));
    }

    @Test
    void unknownFieldsAndInvalidValuesAreRefused() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        update(ana, WEBSITE, T_LAYOUT, "{\"created_by_member_id\":\"" + ACME_BEN + "\"}")
                .andExpect(status().isUnprocessableEntity());
        update(ana, WEBSITE, T_LAYOUT, "{\"priority\":\"HIGH\"}").andExpect(status().isUnprocessableEntity());
        update(ana, WEBSITE, T_LAYOUT, "{\"due_date\":\"2026-02-30\"}").andExpect(status().isUnprocessableEntity());
        update(ana, WEBSITE, T_LAYOUT, "{\"title\":null}").andExpect(status().isUnprocessableEntity());
        update(ana, WEBSITE, T_LAYOUT, "{\"owner_member_id\":null}").andExpect(status().isUnprocessableEntity());
        update(ana, WEBSITE, T_LAYOUT, "{\"title\":").andExpect(status().isBadRequest());
    }

    // --- Edit -----------------------------------------------------------------------------------------------------

    @Test
    void projectManagerEditsFieldsAndReassignsTheTask() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        update(ana, WEBSITE, T_LAYOUT, "{\"title\":\" Approve final layout \",\"description\":\"Both pages.\","
                + "\"due_date\":\"2026-11-22\",\"owner_member_id\":\"" + ACME_BEN + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Approve final layout"))
                .andExpect(jsonPath("$.description").value("Both pages."))
                .andExpect(jsonPath("$.due_date").value("2026-11-22"))
                .andExpect(jsonPath("$.owner_member_id").value(ACME_BEN))
                .andExpect(jsonPath("$.owner_name").value("Ben Demo"));
        // null clears the optional fields; an empty body changes nothing.
        update(ana, WEBSITE, T_LAYOUT, "{\"description\":null,\"due_date\":null}").andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.due_date").doesNotExist());
        update(ana, WEBSITE, T_LAYOUT, "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Approve final layout"));
        // The new owner must be a member of the project (BR-09, AC-10.3).
        update(ana, WEBSITE, T_LAYOUT, "{\"owner_member_id\":\"" + ACME_CARLA + "\"}")
                .andExpect(status().isUnprocessableEntity());
        update(ana, WEBSITE, T_LAYOUT, "{\"owner_member_id\":\"" + NW_CARLA + "\"}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void statusChangesPersistAndCountsFollow() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        update(ana, WEBSITE, T_LAYOUT, "{\"status\":\"Done\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Done"));
        entityManager.flush();
        entityManager.clear();
        assertThat(jdbc.queryForObject("SELECT status FROM task WHERE task_id = ?::uuid", String.class, T_LAYOUT))
                .isEqualTo("DONE");
        mvc.perform(get(path(WEBSITE, T_LAYOUT)).session(signIn("vera@example.com")))
                .andExpect(jsonPath("$.status").value("Done"));
        list(ana, WEBSITE).andExpect(jsonPath("$.status_counts[*].count", contains(0, 1, 2)));
        mvc.perform(get("/progress").param("project_id", WEBSITE).session(ana))
                .andExpect(jsonPath("$[0].done").value(2));
    }

    @Test
    void teamMemberEditsOnlyTasksTheyOwnAndNeverReassigns() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        update(ben, WEBSITE, T_IMAGES, "{\"title\":\"Review all homepage images\",\"status\":\"Done\"}")
                .andExpect(status().isOk());
        update(ben, WEBSITE, T_LAYOUT, "{\"title\":\"Not mine\"}").andExpect(status().isForbidden());
        update(ben, WEBSITE, T_LAYOUT, "{\"status\":\"Done\"}").andExpect(status().isForbidden());
        // A refused request changes nothing, even the fields Ben may change.
        update(ben, WEBSITE, T_IMAGES, "{\"title\":\"Changed\",\"owner_member_id\":\"" + ACME_ANA + "\"}")
                .andExpect(status().isForbidden());
        mvc.perform(get(path(WEBSITE, T_IMAGES)).session(ben))
                .andExpect(jsonPath("$.title").value("Review all homepage images"))
                .andExpect(jsonPath("$.owner_member_id").value(ACME_BEN));
    }

    @Test
    void onlyTheOwnerChangesTheStatusEvenForProjectManagers() throws Exception {
        update(signIn("ana@example.com"), WEBSITE, T_IMAGES, "{\"status\":\"Done\"}").andExpect(status().isForbidden());
    }

    @Test
    void viewersAndWorkspaceAdministratorsReadButNeverChange() throws Exception {
        for (String email : new String[] {"vera@example.com", "carla@example.com"}) {
            MockHttpSession s = signIn(email);
            list(s, WEBSITE).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3));
            mvc.perform(get(path(WEBSITE, T_IMAGES)).session(s)).andExpect(status().isOk());
            update(s, WEBSITE, T_IMAGES, "{\"title\":\"Changed\"}").andExpect(status().isForbidden());
            update(s, WEBSITE, T_IMAGES, "{}").andExpect(status().isForbidden());
            remove(s, WEBSITE, T_IMAGES).andExpect(status().isForbidden());
            create(s, WEBSITE, titled("Not allowed")).andExpect(status().isForbidden());
        }
    }

    // --- Delete ---------------------------------------------------------------------------------------------------

    @Test
    void deletingATaskRemovesItsCommentsAndNotificationsAndUpdatesTheCounts() throws Exception {
        jdbc.update("INSERT INTO notification (recipient_member_id, type, task_id) VALUES (?::uuid, 'TASK_ASSIGNED', ?::uuid)",
                ACME_BEN, T_IMAGES);
        jdbc.update("INSERT INTO notification (recipient_member_id, type, task_id, comment_id) "
                + "VALUES (?::uuid, 'COMMENT_ADDED', ?::uuid, 'f0000000-0000-0000-0000-000000000001'::uuid)",
                ACME_BEN, T_IMAGES);
        assertThat(count("SELECT count(*) FROM comment WHERE task_id = ?::uuid", T_IMAGES)).isEqualTo(2);

        MockHttpSession ana = signIn("ana@example.com");
        remove(ana, WEBSITE, T_IMAGES).andExpect(status().isNoContent());
        entityManager.clear();

        assertThat(count("SELECT count(*) FROM task WHERE task_id = ?::uuid", T_IMAGES)).isZero();
        assertThat(count("SELECT count(*) FROM comment WHERE task_id = ?::uuid", T_IMAGES)).isZero();
        assertThat(count("SELECT count(*) FROM notification WHERE task_id = ?::uuid", T_IMAGES)).isZero();
        assertThat(count("SELECT count(*) FROM comment c WHERE NOT EXISTS "
                + "(SELECT 1 FROM task t WHERE t.task_id = c.task_id)")).isZero();
        assertThat(count("SELECT count(*) FROM notification n WHERE NOT EXISTS "
                + "(SELECT 1 FROM task t WHERE t.task_id = n.task_id)")).isZero();

        mvc.perform(get(path(WEBSITE, T_IMAGES)).session(ana)).andExpect(status().isNotFound());
        remove(ana, WEBSITE, T_IMAGES).andExpect(status().isNotFound());
        list(ana, WEBSITE).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.status_counts[*].count", contains(1, 0, 1)));
    }

    @Test
    void onlyTheCreatorDeletesATask() throws Exception {
        // AC-26.2: "Review homepage images" was created by Ana, so Ben (its owner) cannot delete it.
        MockHttpSession ben = signIn("ben@example.com");
        remove(ben, WEBSITE, T_IMAGES).andExpect(status().isForbidden());
        remove(ben, WEBSITE, T_PUBLISH).andExpect(status().isNoContent());
    }

    // --- List: filters, order and pages ---------------------------------------------------------------------------

    @Test
    void filtersCombineAndPagesFollowTheContract() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        // Order: earliest due date first (images 11-04, publish 11-10, layout 11-20).
        list(ana, WEBSITE).andExpect(jsonPath("$.items[*].task_id", contains(T_IMAGES, T_PUBLISH, T_LAYOUT)))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
        list(ana, WEBSITE, "status", "To do").andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].task_id").value(T_LAYOUT))
                .andExpect(jsonPath("$.status_counts[*].count", contains(1, 1, 1)));
        list(ana, WEBSITE, "owner_member_id", ACME_BEN).andExpect(jsonPath("$.total").value(2));
        list(ana, WEBSITE, "owner_member_id", ACME_BEN, "status", "Done").andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].task_id").value(T_PUBLISH));
        list(ana, WEBSITE, "owner_member_id", ACME_VERA).andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items", hasSize(0)));

        // Pages apply to the filtered result.
        list(ana, WEBSITE, "owner_member_id", ACME_BEN, "size", "1").andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].task_id").value(T_IMAGES));
        list(ana, WEBSITE, "owner_member_id", ACME_BEN, "size", "1", "page", "1")
                .andExpect(jsonPath("$.items[0].task_id").value(T_PUBLISH));
        list(ana, WEBSITE, "owner_member_id", ACME_BEN, "size", "1", "page", "2")
                .andExpect(jsonPath("$.items", hasSize(0))).andExpect(jsonPath("$.total").value(2));

        // A task without a due date comes last.
        create(ana, WEBSITE, titled("Undated task")).andExpect(status().isCreated());
        list(ana, WEBSITE).andExpect(jsonPath("$.items[3].title").value("Undated task"));

        for (String[] bad : new String[][] {{"page", "-1"}, {"size", "0"}, {"size", "101"}, {"page", "x"},
                {"owner_member_id", "not-an-id"}}) {
            list(ana, WEBSITE, bad[0], bad[1]).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.details[0].field").value(bad[0]));
        }
    }

    // --- Workspace isolation and project/task id mismatch ---------------------------------------------------------

    @Test
    void userOfAnotherWorkspaceCannotReachTheProjectsTasks() throws Exception {
        MockHttpSession dan = signIn("dan@example.com");
        list(dan, WEBSITE).andExpect(status().isNotFound());
        mvc.perform(get(path(WEBSITE, T_IMAGES)).session(dan)).andExpect(status().isNotFound());
        create(dan, WEBSITE, titled("Intruder")).andExpect(status().isNotFound());
        update(dan, WEBSITE, T_IMAGES, "{\"title\":\"Intruder\"}").andExpect(status().isNotFound());
        remove(dan, WEBSITE, T_IMAGES).andExpect(status().isNotFound());
        update(dan, WEBSITE, T_IMAGES, "{\"status\":\"Done\"}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
        // Through his own project, a foreign task id is not found either.
        mvc.perform(get(path(LAB, T_IMAGES)).session(dan)).andExpect(status().isNotFound());
        assertThat(count("SELECT count(*) FROM task WHERE title = 'Intruder'")).isZero();
    }

    @Test
    void aTaskIsOnlyReachableThroughItsOwnProject() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        // Ana can see both projects, but "Write kickoff notes" belongs to Trade fair stand, not Website redesign.
        mvc.perform(get(path(TRADE_FAIR, T_KICKOFF)).session(ana)).andExpect(status().isOk());
        mvc.perform(get(path(WEBSITE, T_KICKOFF)).session(ana)).andExpect(status().isNotFound());
        update(ana, WEBSITE, T_KICKOFF, "{\"title\":\"Moved\"}").andExpect(status().isNotFound());
        remove(ana, WEBSITE, T_KICKOFF).andExpect(status().isNotFound());
        // Lab inventory is in Northwind, where Ana is a Team Member: the Website task is not found there.
        mvc.perform(get(path(LAB, T_IMAGES)).session(ana)).andExpect(status().isNotFound());
        mvc.perform(get(path(WEBSITE, "not-an-id")).session(ana)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT project_id::text FROM task WHERE task_id = ?::uuid", String.class,
                T_KICKOFF)).isEqualTo(TRADE_FAIR);
    }

    @Test
    void everyTaskOperationNeedsASession() throws Exception {
        mvc.perform(get("/projects/" + WEBSITE + "/tasks")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("not_authenticated"));
        mvc.perform(get(path(WEBSITE, T_IMAGES))).andExpect(status().isUnauthorized());
        mvc.perform(post("/projects/" + WEBSITE + "/tasks").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(titled("No session"))).andExpect(status().isUnauthorized());
        mvc.perform(patch(path(WEBSITE, T_IMAGES)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(delete(path(WEBSITE, T_IMAGES)).with(csrf())).andExpect(status().isUnauthorized());
    }
}
