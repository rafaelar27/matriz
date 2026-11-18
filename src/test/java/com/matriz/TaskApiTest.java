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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
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

    /** Number of stored tasks (pending writes flushed first, so the count includes this test's own changes). */
    private long taskCount() {
        entityManager.flush();
        return count("SELECT count(*) FROM task");
    }

    /** The stored row of a task, read from the database; fails when the row does not exist. */
    private Map<String, Object> snapshot(String taskId) {
        entityManager.flush();
        entityManager.clear();
        return jdbc.queryForMap("SELECT project_id::text AS project, title, description, due_date::text AS due, "
                + "status, owner_member_id::text AS owner, created_by_member_id::text AS creator "
                + "FROM task WHERE task_id = ?::uuid", taskId);
    }

    private static String bodyOf(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    /** The task ids of one page of a task list. */
    private static List<String> idsOf(ResultActions page) throws Exception {
        return JsonPath.read(bodyOf(page), "$.items[*].task_id");
    }

    /** Creates a task owned by Ana through the API as the given session and returns its id. */
    private String make(MockHttpSession s, String project, String title, String dueDate) throws Exception {
        String due = dueDate == null ? "" : ",\"due_date\":\"" + dueDate + "\"";
        String body = bodyOf(create(s, project, "{\"title\":\"" + title + "\",\"owner_member_id\":\"" + ACME_ANA
                + "\"" + due + "}").andExpect(status().isCreated()));
        return JsonPath.read(body, "$.task_id");
    }

    private int progressTotal(MockHttpSession s, String project) throws Exception {
        String body = bodyOf(mvc.perform(get("/progress").param("project_id", project).session(s))
                .andExpect(status().isOk()));
        return JsonPath.read(body, "$[0].total");
    }

    // --- Create: defaults and validation --------------------------------------------------------------------------

    @Test
    void createWithoutStatusUsesTheDefaultAndAppearsInTheList() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        String body = create(ana, WEBSITE, "{\"title\":\"  Check page speed  \",\"owner_member_id\":\"" + ACME_BEN
                + "\",\"due_date\":\"2026-11-25\"}")
                .andExpect(status().isCreated())
                // openapi createTask 201: Location is the path of the new task, project-scoped.
                .andExpect(result -> header().string("Location", "/projects/" + WEBSITE + "/tasks/"
                        + JsonPath.<String>read(result.getResponse().getContentAsString(), "$.task_id")).match(result))
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
        long tasksBefore = taskCount();
        for (String bad : new String[] {"DONE", "done", "Concluída", "Blocked", ""}) {
            create(ana, WEBSITE, "{\"title\":\"Bad status\",\"owner_member_id\":\"" + ACME_ANA + "\",\"status\":\""
                    + bad + "\"}").andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("validation_failed"))
                    .andExpect(jsonPath("$.details[0].field").value("status"));
            assertThat(taskCount()).as("nothing created for status '" + bad + "'").isEqualTo(tasksBefore);
            update(ana, WEBSITE, T_LAYOUT, "{\"status\":\"" + bad + "\"}").andExpect(status().isUnprocessableEntity());
        }
        update(ana, WEBSITE, T_LAYOUT, "{\"status\":null}").andExpect(status().isUnprocessableEntity());
        list(ana, WEBSITE, "status", "TODO").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("status"));
    }

    @Test
    void unknownFieldsAndInvalidValuesAreRefused() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        Map<String, Object> before = snapshot(T_LAYOUT);
        // {json, field of details[0] or null when the contract names no field}
        String[][] invalid = {
            {"{\"created_by_member_id\":\"" + ACME_BEN + "\"}", "created_by_member_id"},
            {"{\"priority\":\"HIGH\"}", null},
            {"{\"due_date\":\"2026-02-30\"}", "due_date"},
            {"{\"due_date\":\"abc\"}", "due_date"},
            {"{\"title\":null}", "title"},
            {"{\"owner_member_id\":null}", "owner_member_id"},
        };
        for (String[] c : invalid) {
            ResultActions r = update(ana, WEBSITE, T_LAYOUT, c[0]).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("validation_failed"));
            if (c[1] != null) {
                r.andExpect(jsonPath("$.details[*].field").value(org.hamcrest.Matchers.hasItem(c[1])));
            }
            assertThat(snapshot(T_LAYOUT)).as("unchanged after " + c[0]).isEqualTo(before);
        }
        update(ana, WEBSITE, T_LAYOUT, "{\"title\":").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed_request"));
        assertThat(snapshot(T_LAYOUT)).isEqualTo(before);
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
            Map<String, Object> before = snapshot(T_IMAGES);
            long tasks = taskCount();
            list(s, WEBSITE).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3));
            mvc.perform(get(path(WEBSITE, T_IMAGES)).session(s)).andExpect(status().isOk());
            update(s, WEBSITE, T_IMAGES, "{\"title\":\"Changed\"}").andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("forbidden"));
            update(s, WEBSITE, T_IMAGES, "{}").andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("forbidden"));
            remove(s, WEBSITE, T_IMAGES).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("forbidden"));
            create(s, WEBSITE, titled("Not allowed")).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("forbidden"));
            // TD-03 (b, c) and TU-03 (d): a refused request changed nothing and the task still exists.
            assertThat(count("SELECT count(*) FROM task WHERE task_id = ?::uuid", T_IMAGES)).as(email).isEqualTo(1);
            assertThat(snapshot(T_IMAGES)).as(email).isEqualTo(before);
            assertThat(taskCount()).as(email).isEqualTo(tasks);
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
        int progressBefore = progressTotal(ana, WEBSITE);
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
        // TD-01, AC-17.3: the progress total drops by one.
        assertThat(progressTotal(ana, WEBSITE)).isEqualTo(progressBefore - 1);
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

    @Test
    @DisplayName("S-01 · FR-13, listTasks · page*size above 2147483647 is refused with 422 on field page, never 500")
    void hugePageIsRefusedWithValidationFailed() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        list(ana, WEBSITE, "page", "2147483647", "size", "100").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.details[0].field").value("page"));
    }

    @Test
    @DisplayName("S-01 · FR-13, listTasks · guard: a large but valid page is 200 with no items, page 0 still works")
    void largeValidPageStillAnswersWithEmptyItems() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        list(ana, WEBSITE, "page", "21474836", "size", "100").andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0))).andExpect(jsonPath("$.total").value(3));
        list(ana, WEBSITE, "page", "0", "size", "20").andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)));
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
    @DisplayName("TA-01 · FR-01, SP-01 · every task operation without a session is 401 not_authenticated and changes nothing")
    void everyTaskOperationNeedsASession() throws Exception {
        Map<String, Object> before = snapshot(T_IMAGES);
        long tasks = taskCount();
        mvc.perform(get("/projects/" + WEBSITE + "/tasks")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("not_authenticated"));
        mvc.perform(get(path(WEBSITE, T_IMAGES))).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("not_authenticated"));
        mvc.perform(post("/projects/" + WEBSITE + "/tasks").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(titled("No session"))).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("not_authenticated"));
        mvc.perform(patch(path(WEBSITE, T_IMAGES)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"No session change\"}")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("not_authenticated"));
        mvc.perform(delete(path(WEBSITE, T_IMAGES)).with(csrf())).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("not_authenticated"));
        assertThat(snapshot(T_IMAGES)).isEqualTo(before);
        assertThat(taskCount()).isEqualTo(tasks);
    }

    // --- New scenarios (test-plan matrix; expected results from openapi.yaml, user-stories.md, database.md) -------

    @Test
    @DisplayName("TC-03 · FR-12, BR-11, AC-10.1, A3 · only title and owner: 201, status To do, description and due_date null or absent")
    void titleAndOwnerOnlyUseTheDefaults() throws Exception {
        String body = bodyOf(create(signIn("ana@example.com"), WEBSITE, titled("Only the basics"))
                .andExpect(status().isCreated()));
        Map<String, Object> task = JsonPath.read(body, "$");
        assertThat(task.get("status")).isEqualTo("To do");
        assertThat(task.get("description")).isNull();
        assertThat(task.get("due_date")).isNull();
        assertThat(task.get("owner_member_id")).isEqualTo(ACME_ANA);
    }

    @Test
    @DisplayName("TV-06 · FR-11, createTask · an invalid calendar date on create is 422 on field due_date and nothing is created")
    void invalidDueDateOnCreateIsRefused() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        long tasks = taskCount();
        for (String bad : new String[] {"2026-02-30", "31/12/2026", "abc"}) {
            create(ana, WEBSITE, "{\"title\":\"Bad date\",\"owner_member_id\":\"" + ACME_ANA + "\",\"due_date\":\""
                    + bad + "\"}").andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("validation_failed"))
                    .andExpect(jsonPath("$.details[*].field").value(org.hamcrest.Matchers.hasItem("due_date")));
            assertThat(taskCount()).as("nothing created for " + bad).isEqualTo(tasks);
        }
    }

    @Test
    @DisplayName("TV-07 · openapi Identity, A5, F17 out of scope · extra fields priority and task_id on create are 422 and nothing is created")
    void unknownFieldsOnCreateAreRefused() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        long tasks = taskCount();
        for (String extra : new String[] {"\"priority\":\"HIGH\"", "\"task_id\":\"" + T_LAYOUT + "\""}) {
            create(ana, WEBSITE, "{\"title\":\"Extra field\",\"owner_member_id\":\"" + ACME_ANA + "\"," + extra + "}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("validation_failed"));
            assertThat(taskCount()).as("nothing created with " + extra).isEqualTo(tasks);
        }
    }

    @Test
    @DisplayName("TV-08 · API-A2, A6 · invalid JSON and a missing body on create are 400 malformed_request and nothing is created")
    void malformedBodyOnCreateIsABadRequest() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        long tasks = taskCount();
        create(ana, WEBSITE, "{\"title\":").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed_request"));
        mvc.perform(post("/projects/" + WEBSITE + "/tasks").session(ana).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("malformed_request"));
        assertThat(taskCount()).isEqualTo(tasks);
    }

    @Test
    @DisplayName("TL-01 · FR-05, FR-08, FR-13, AC-19.1 · a Viewer, a Workspace Administrator, a Project Manager and a Team Member "
            + "outside the project list the same tasks")
    void everyActiveWorkspaceMemberListsTheSameTasks() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        // Ben stands in for a Team Member who is not a member of the project (Tom in the plan).
        jdbc.update("DELETE FROM project_member WHERE project_id = ?::uuid AND member_id = ?::uuid", TRADE_FAIR, ACME_BEN);
        entityManager.clear();
        List<List<String>> lists = new ArrayList<>();
        for (MockHttpSession s : new MockHttpSession[] {signIn("vera@example.com"), signIn("carla@example.com"),
                signIn("ana@example.com"), ben}) {
            ResultActions page = list(s, TRADE_FAIR).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
            lists.add(idsOf(page));
        }
        for (List<String> ids : lists) {
            assertThat(ids).containsExactly(T_KICKOFF);
        }
    }

    @Test
    @DisplayName("TL-02 · FR-13, decision log 2026-11-12, A9 · order: due date, undated last, title ignoring case, then id; "
            + "item fields and the three status_counts in order")
    void listOrderFieldsAndCounts() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        // Trade fair stand already has "Write kickoff notes" (undated, To do).
        String b = make(ana, TRADE_FAIR, "bbb", "2026-11-09");
        String a = make(ana, TRADE_FAIR, "aaa", "2026-11-02");
        String beta = make(ana, TRADE_FAIR, "Beta", null);
        String alpha = make(ana, TRADE_FAIR, "alpha", null);
        String twinUpper = make(ana, TRADE_FAIR, "Twin", "2026-11-05");
        String twinLower = make(ana, TRADE_FAIR, "twin", "2026-11-05");
        List<String> twins = new ArrayList<>(List.of(twinUpper, twinLower));
        twins.sort(String::compareTo);
        List<String> expected = new ArrayList<>(List.of(a));
        expected.addAll(twins);
        expected.addAll(List.of(b, alpha, beta, T_KICKOFF));

        ResultActions page = list(ana, TRADE_FAIR).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(7))
                .andExpect(jsonPath("$.status_counts[*].status", contains("To do", "In progress", "Done")))
                .andExpect(jsonPath("$.status_counts[*].count", contains(7, 0, 0)));
        assertThat(idsOf(page)).containsExactlyElementsOf(expected);
        String body = bodyOf(page);
        for (int i = 0; i < 7; i++) {
            for (String field : new String[] {"task_id", "project_id", "title", "owner_member_id", "status",
                    "created_by_member_id"}) {
                assertThat((Object) JsonPath.read(body, "$.items[" + i + "]." + field)).as(field + " of item " + i).isNotNull();
            }
        }
        assertThat((String) JsonPath.read(body, "$.items[0].project_id")).isEqualTo(TRADE_FAIR);
        assertThat((String) JsonPath.read(body, "$.items[0].due_date")).isEqualTo("2026-11-02");
    }

    @Test
    @DisplayName("TL-04 · FR-13, listTasks · 25 tasks: default size 20, page=1&size=10, each task on exactly one page, total 25")
    void pagingCoversEveryTaskOnce() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        for (int i = 1; i <= 22; i++) {
            make(ana, WEBSITE, "Bulk task " + String.format("%02d", i), null);
        }
        entityManager.flush();
        Set<String> all = new HashSet<>(jdbc.queryForList("SELECT task_id::text FROM task WHERE project_id = ?::uuid",
                String.class, WEBSITE));
        assertThat(all).hasSize(25);

        ResultActions first = list(ana, WEBSITE).andExpect(jsonPath("$.total").value(25))
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.items", hasSize(20)));
        List<String> defaultPages = new ArrayList<>(idsOf(first));
        ResultActions second = list(ana, WEBSITE, "page", "1").andExpect(jsonPath("$.total").value(25))
                .andExpect(jsonPath("$.items", hasSize(5)));
        defaultPages.addAll(idsOf(second));
        assertThat(defaultPages).hasSize(25).doesNotHaveDuplicates();
        assertThat(new HashSet<>(defaultPages)).isEqualTo(all);

        List<String> tens = new ArrayList<>();
        int[] expectedSizes = {10, 10, 5, 0};
        for (int page = 0; page < 4; page++) {
            ResultActions r = list(ana, WEBSITE, "size", "10", "page", String.valueOf(page))
                    .andExpect(jsonPath("$.total").value(25)).andExpect(jsonPath("$.size").value(10))
                    .andExpect(jsonPath("$.items", hasSize(expectedSizes[page])));
            tens.addAll(idsOf(r));
        }
        assertThat(tens).hasSize(25).doesNotHaveDuplicates();
        assertThat(new HashSet<>(tens)).isEqualTo(all);
    }

    @Test
    @DisplayName("TU-01 · US-25, updateTask (PD-01 prov.) · (a) a Project Manager outside the project edits title, description and due date; "
            + "(b) a Team Member edits their own task; owner, status and creator unchanged")
    void allowedEditorsChangeTitleDescriptionAndDueDate() throws Exception {
        // (a) Dan is a Project Manager of Northwind Lab and not a project member of Lab inventory.
        Map<String, Object> lab = snapshot(T_EQUIPMENT);
        update(signIn("dan@example.com"), LAB, T_EQUIPMENT, "{\"title\":\"Count all lab equipment\","
                + "\"description\":\"Include the storage room.\",\"due_date\":\"2026-12-05\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Count all lab equipment"))
                .andExpect(jsonPath("$.description").value("Include the storage room."))
                .andExpect(jsonPath("$.due_date").value("2026-12-05"))
                .andExpect(jsonPath("$.owner_member_id").value(lab.get("owner")))
                .andExpect(jsonPath("$.status").value("To do"))
                .andExpect(jsonPath("$.created_by_member_id").value(lab.get("creator")));
        Map<String, Object> labAfter = snapshot(T_EQUIPMENT);
        assertThat(labAfter.get("title")).isEqualTo("Count all lab equipment");
        assertThat(labAfter.get("owner")).isEqualTo(lab.get("owner"));
        assertThat(labAfter.get("status")).isEqualTo(lab.get("status"));
        assertThat(labAfter.get("creator")).isEqualTo(lab.get("creator"));

        // (b) Ben owns "Review homepage images" and is a Team Member.
        Map<String, Object> images = snapshot(T_IMAGES);
        update(signIn("ben@example.com"), WEBSITE, T_IMAGES, "{\"title\":\"Review every homepage image\","
                + "\"description\":\"Check the sizes too.\",\"due_date\":\"2026-11-06\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Review every homepage image"))
                .andExpect(jsonPath("$.description").value("Check the sizes too."))
                .andExpect(jsonPath("$.due_date").value("2026-11-06"))
                .andExpect(jsonPath("$.owner_member_id").value(ACME_BEN))
                .andExpect(jsonPath("$.status").value("In progress"))
                .andExpect(jsonPath("$.created_by_member_id").value(ACME_ANA));
        Map<String, Object> imagesAfter = snapshot(T_IMAGES);
        assertThat(imagesAfter.get("title")).isEqualTo("Review every homepage image");
        assertThat(imagesAfter.get("owner")).isEqualTo(images.get("owner"));
        assertThat(imagesAfter.get("status")).isEqualTo(images.get("status"));
        assertThat(imagesAfter.get("creator")).isEqualTo(images.get("creator"));
    }

    @Test
    @DisplayName("TU-03 (c) · FR-05, AC-19.2, PD-17 prov. · a Viewer who owns the task cannot edit its title: 403 forbidden, unchanged")
    void viewerWhoOwnsATaskCannotEditIt() throws Exception {
        MockHttpSession vera = signIn("vera@example.com");
        jdbc.update("UPDATE task SET owner_member_id = ?::uuid WHERE task_id = ?::uuid", ACME_VERA, T_LAYOUT);
        Map<String, Object> before = snapshot(T_LAYOUT);
        update(vera, WEBSITE, T_LAYOUT, "{\"title\":\"Viewer rename\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
        assertThat(snapshot(T_LAYOUT)).isEqualTo(before);
    }

    @Test
    @DisplayName("TU-03 (d) · PD-03 prov. · a Workspace Administrator cannot edit a task: 403 forbidden, unchanged")
    void workspaceAdministratorCannotEditATask() throws Exception {
        Map<String, Object> before = snapshot(T_LAYOUT);
        update(signIn("carla@example.com"), WEBSITE, T_LAYOUT, "{\"title\":\"Admin rename\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
        assertThat(snapshot(T_LAYOUT)).isEqualTo(before);
    }

    @Test
    @DisplayName("TD-03 (a) · AC-26.2, BR-12 · a Project Manager who did not create the task cannot delete it: 403 forbidden, task remains")
    void projectManagerWhoIsNotTheCreatorCannotDelete() throws Exception {
        // "Publish new homepage" was created by Ben.
        MockHttpSession ana = signIn("ana@example.com");
        Map<String, Object> before = snapshot(T_PUBLISH);
        remove(ana, WEBSITE, T_PUBLISH).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
        assertThat(snapshot(T_PUBLISH)).isEqualTo(before);
        assertThat(count("SELECT count(*) FROM task WHERE task_id = ?::uuid", T_PUBLISH)).isEqualTo(1);
    }

    @Test
    @DisplayName("TD-03 (d) · openapi deleteTask, FR-05 · a creator who is now a Viewer cannot delete their own task: 403 forbidden, task remains")
    void creatorWhoIsNowAViewerCannotDelete() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        jdbc.update("UPDATE member SET role = 'VIEWER' WHERE member_id = ?::uuid", ACME_BEN);
        entityManager.clear();
        Map<String, Object> before = snapshot(T_PUBLISH);
        remove(ben, WEBSITE, T_PUBLISH).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
        assertThat(snapshot(T_PUBLISH)).isEqualTo(before);
        assertThat(count("SELECT count(*) FROM task WHERE task_id = ?::uuid", T_PUBLISH)).isEqualTo(1);
    }

    @Test
    @DisplayName("TA-03 reverse · NFR-01, SP-02, API-A3 · a member of Acme only gets the same 404 not_found for Northwind's project and task, with no task data")
    void memberOfAcmeCannotReadNorthwindTasks() throws Exception {
        MockHttpSession vera = signIn("vera@example.com");
        // Reference: an unknown task id in Vera's own project.
        String unknown = bodyOf(mvc.perform(get(path(WEBSITE, "e0000000-0000-0000-0000-000000000999")).session(vera))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("not_found")));
        assertThat(unknown).isNotBlank();
        String listBody = bodyOf(list(vera, LAB).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found")));
        String oneBody = bodyOf(mvc.perform(get(path(LAB, T_EQUIPMENT)).session(vera)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found")));
        for (String body : new String[] {listBody, oneBody}) {
            Map<String, Object> json = JsonPath.read(body, "$");
            assertThat(json).doesNotContainKeys("items", "title", "task_id");
            assertThat(body).doesNotContain("Count lab equipment");
        }
    }

    @Test
    @DisplayName("TU-06 (title) · US-25, AC-25.1, AC-25.2, AC-10.5, BR-10, TaskValidationFailed · PATCH title \"\", spaces, \"ab\", \"  ab  \" "
            + "and 151 characters: 422 validation_failed on field title, the whole task row unchanged")
    void invalidTitlesOnEditAreRefusedAndTheRowIsUnchanged() throws Exception {
        // Already asserted elsewhere (code, field, title unchanged): "", spaces, "  ab  " and 151 characters in
        // VerificationApiTest.case04UpdateRefusesInvalidTitles; title null in unknownFieldsAndInvalidValuesAreRefused.
        // Missing there: plain "ab", and the other columns unchanged. This test covers both for the five values.
        MockHttpSession ana = signIn("ana@example.com");
        Map<String, Object> before = snapshot(T_LAYOUT);
        long tasks = taskCount();
        for (String title : new String[] {"", "   ", "ab", "  ab  ", "a".repeat(151)}) {
            update(ana, WEBSITE, T_LAYOUT, "{\"title\":\"" + title + "\"}").andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("validation_failed"))
                    .andExpect(jsonPath("$.details[*].field").value(org.hamcrest.Matchers.hasItem("title")));
            assertThat(snapshot(T_LAYOUT)).as("row unchanged after title of length " + title.length())
                    .isEqualTo(before);
            assertThat(taskCount()).isEqualTo(tasks);
        }
    }

    @Test
    @DisplayName("TU-06 (description, owner) · US-25, AC-10.3, AC-25.1, TaskValidationFailed · PATCH description of 2001 characters, "
            + "owner null and owner outside the project: 422 validation_failed on the named field, the row unchanged")
    void invalidDescriptionAndOwnerOnEditAreRefusedAndTheRowIsUnchanged() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        Map<String, Object> before = snapshot(T_LAYOUT);
        String[][] invalid = {
            {"{\"description\":\"" + "d".repeat(2001) + "\"}", "description"},
            {"{\"owner_member_id\":null}", "owner_member_id"},
            // Carla (Acme administrator) is not a member of Website redesign; Northwind's Carla is another workspace.
            {"{\"owner_member_id\":\"" + ACME_CARLA + "\"}", "owner_member_id"},
            {"{\"owner_member_id\":\"" + NW_CARLA + "\"}", "owner_member_id"},
        };
        for (String[] c : invalid) {
            update(ana, WEBSITE, T_LAYOUT, c[0]).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("validation_failed"))
                    .andExpect(jsonPath("$.details[*].field").value(org.hamcrest.Matchers.hasItem(c[1])));
            assertThat(snapshot(T_LAYOUT)).as("row unchanged after " + c[1]).isEqualTo(before);
        }
    }

    @Test
    @DisplayName("S-07 security review · BR-09, FR-10, createTask · a deactivated member of the project is refused as owner: "
            + "422 validation_failed on owner_member_id and no task is created")
    void deactivatedMemberIsRefusedAsOwnerOnCreate() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        long before = taskCount();
        // Only inside this test's rolled-back transaction.
        jdbc.update("UPDATE member SET status = 'DEACTIVATED' WHERE member_id = ?::uuid", ACME_BEN);
        entityManager.clear();

        create(ana, WEBSITE, "{\"title\":\"Fictitious deactivated owner\",\"owner_member_id\":\"" + ACME_BEN + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.details[0].field").value("owner_member_id"));

        assertThat(taskCount()).isEqualTo(before);
        assertThat(count("SELECT count(*) FROM task WHERE project_id = ?::uuid", WEBSITE)).isEqualTo(3);
    }

    @Test
    @DisplayName("S-07 security review · BR-09, FR-10, updateTask · a deactivated member of the project is refused as the new owner: "
            + "422 validation_failed on owner_member_id and the task is unchanged")
    void deactivatedMemberIsRefusedAsOwnerOnEdit() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        Map<String, Object> before = snapshot(T_LAYOUT);
        jdbc.update("UPDATE member SET status = 'DEACTIVATED' WHERE member_id = ?::uuid", ACME_BEN);
        entityManager.clear();

        update(ana, WEBSITE, T_LAYOUT, "{\"owner_member_id\":\"" + ACME_BEN + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.details[0].field").value("owner_member_id"));

        assertThat(snapshot(T_LAYOUT)).isEqualTo(before);
    }

    @Test
    @DisplayName("S-07 security review · BR-09, FR-10 · guard: an active member of the project is still accepted as owner on create and on edit")
    void activeMemberIsStillAcceptedAsOwner() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        create(ana, WEBSITE, "{\"title\":\"Fictitious active owner\",\"owner_member_id\":\"" + ACME_BEN + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.owner_member_id").value(ACME_BEN));
        update(ana, WEBSITE, T_LAYOUT, "{\"owner_member_id\":\"" + ACME_BEN + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner_member_id").value(ACME_BEN));
        assertThat(snapshot(T_LAYOUT).get("owner")).isEqualTo(ACME_BEN);
    }

    /** PATCH and DELETE of a task of another workspace through the caller's own project: 404, nothing changed. */
    private void assertForeignTaskUntouched(String email, String ownProject, String foreignTask) throws Exception {
        MockHttpSession session = signIn(email);
        Map<String, Object> before = snapshot(foreignTask);
        long tasks = taskCount();
        String title = (String) before.get("title");
        for (String body : new String[] {"{\"status\":\"Done\"}", "{\"title\":\"Hijacked\"}"}) {
            String response = bodyOf(update(session, ownProject, foreignTask, body).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("not_found")));
            assertNoTaskData(response, title, foreignTask);
            assertThat(snapshot(foreignTask)).as(email + " row unchanged after PATCH " + body).isEqualTo(before);
        }
        String response = bodyOf(remove(session, ownProject, foreignTask).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found")));
        assertNoTaskData(response, title, foreignTask);
        assertThat(count("SELECT count(*) FROM task WHERE task_id = ?::uuid", foreignTask)).isEqualTo(1);
        assertThat(snapshot(foreignTask)).as(email + " row unchanged after DELETE").isEqualTo(before);
        assertThat(taskCount()).isEqualTo(tasks);
    }

    private static void assertNoTaskData(String body, String title, String taskId) {
        Map<String, Object> json = JsonPath.read(body, "$");
        assertThat(json).doesNotContainKeys("task_id", "title", "owner_member_id", "status", "items");
        assertThat(body).doesNotContain(title).doesNotContain(taskId);
    }

    @Test
    @DisplayName("TA-05 · NFR-01, SP-02, API-A3, updateTask, deleteTask · Dan (Northwind Lab only) PATCHes and DELETEs an Acme task through his own "
            + "project Lab inventory: 404 not_found, no task data, the Acme task still exists and is unchanged")
    void danCannotChangeOrDeleteAnAcmeTaskThroughHisOwnProject() throws Exception {
        assertForeignTaskUntouched("dan@example.com", LAB, T_IMAGES);
    }

    @Test
    @DisplayName("TA-05 reverse · NFR-01, SP-02, API-A3, updateTask, deleteTask · Vera (Acme only) PATCHes and DELETEs a Northwind task through "
            + "her own project Website redesign: 404 not_found, no task data, the Northwind task is unchanged")
    void veraCannotChangeOrDeleteANorthwindTaskThroughHerOwnProject() throws Exception {
        assertForeignTaskUntouched("vera@example.com", WEBSITE, T_EQUIPMENT);
    }
}
