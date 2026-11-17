package com.matriz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/**
 * QA verification campaign 2026-11-16, scenarios 01–09 (docs/verification.md); US-10, US-11, US-15, US-25, US-29,
 * NFR-01. Covers cases 03 to 08 through direct API requests (openapi.yaml 0.7.0-draft). Demo data: "Website
 * redesign" has Review homepage images (owner Ben, In progress, created by Ana), Approve page layout (owner Ana,
 * To do, created by Ana) and Publish new homepage (owner Ben, Done, created by Ben); project members Ana (Project
 * Manager), Ben (Team Member) and Vera (Viewer). Dan is a Project Manager of Northwind Lab only.
 */
class VerificationApiTest extends ApiTestSupport {

    static final String T_PUBLISH = "e0000000-0000-0000-0000-000000000003";

    @PersistenceContext
    private EntityManager entityManager;

    // --- Request helpers -------------------------------------------------------------------------------------------

    private static String taskPath(String project, String task) {
        return "/projects/" + project + "/tasks/" + task;
    }

    private ResultActions createTask(MockHttpSession s, String project, String json) throws Exception {
        return mvc.perform(post("/projects/" + project + "/tasks").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions updateTask(MockHttpSession s, String project, String task, String json) throws Exception {
        return mvc.perform(patch(taskPath(project, task)).session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions deleteTask(MockHttpSession s, String project, String task) throws Exception {
        return mvc.perform(delete(taskPath(project, task)).session(s).with(csrf()));
    }

    private ResultActions changeStatus(MockHttpSession s, String task, String status) throws Exception {
        return updateTask(s, WEBSITE, task, "{\"status\":\"" + status + "\"}");
    }

    private ResultActions listTasks(MockHttpSession s, String project, String... params) throws Exception {
        var request = get("/projects/" + project + "/tasks").session(s);
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request);
    }

    private ResultActions comment(MockHttpSession s, String project, String task, String jsonBodyValue)
            throws Exception {
        return mvc.perform(post(taskPath(project, task) + "/comments").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + jsonBodyValue + "\"}"));
    }

    private ResultActions assign(MockHttpSession s, String project, String task, String... ids) throws Exception {
        String list = java.util.Arrays.stream(ids).map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
        return mvc.perform(post(taskPath(project, task) + "/assignees").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"member_ids\":[" + list + "]}"));
    }

    private ResultActions unassign(MockHttpSession s, String project, String task, String member) throws Exception {
        return mvc.perform(delete(taskPath(project, task) + "/assignees/" + member).session(s).with(csrf()));
    }

    private ResultActions progress(MockHttpSession s, String project) throws Exception {
        return mvc.perform(get("/progress").param("project_id", project).session(s));
    }

    private static String titled(String jsonTitle) {
        return "{\"title\":\"" + jsonTitle + "\",\"owner_member_id\":\"" + ACME_ANA + "\"}";
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private String storedTitle(String task) {
        return jdbc.queryForObject("SELECT title FROM task WHERE task_id = ?::uuid", String.class, task);
    }

    /**
     * The tests share one persistence context across requests, while a real request starts with an empty one and a
     * refused request is rolled back. Drop what a refused request left in memory, without flushing it.
     */
    private void discardRefusedRequest() {
        entityManager.clear();
    }

    /** Writes what successful requests left in memory, then empties it, so the next read comes from the database. */
    private void writeAndForget() {
        entityManager.flush();
        entityManager.clear();
    }

    /** Every row of the tables an attacker could try to change, in a fixed order. */
    private Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();
        rows.put("project", jdbc.queryForList("SELECT project_id::text, workspace_id::text, name, description, "
                + "owner_member_id::text, start_date::text, due_date::text, is_archived FROM project ORDER BY 1"));
        rows.put("project_member", jdbc.queryForList(
                "SELECT project_id::text, member_id::text FROM project_member ORDER BY 1, 2"));
        rows.put("task", jdbc.queryForList("SELECT task_id::text, project_id::text, title, description, "
                + "owner_member_id::text, due_date::text, status, created_by_member_id::text FROM task ORDER BY 1"));
        rows.put("comment", jdbc.queryForList("SELECT comment_id::text, task_id::text, author_member_id::text, body, "
                + "created_at::text FROM comment ORDER BY 1"));
        rows.put("task_assignee", jdbc.queryForList("SELECT task_id::text, member_id::text, assigned_at::text "
                + "FROM task_assignee ORDER BY 1, 2"));
        return rows;
    }

    // --- Case 03: create, then read back -----------------------------------------------------------------------------

    @Test
    @DisplayName("Case 03 · AC-10.1, FR-09, FR-13, NFR-03 · a created task is read back by another user's new session, "
            + "in the list and counts, and in the database")
    void case03CreatedTaskIsReadBackEverywhere() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        listTasks(ana, WEBSITE).andExpect(status().isOk())
                .andExpect(jsonPath("$.status_counts[0].status").value("To do"))
                .andExpect(jsonPath("$.status_counts[0].count").value(1))
                .andExpect(jsonPath("$.total").value(3));

        String body = createTask(ana, WEBSITE, "{\"title\":\"Verification persistence check\",\"owner_member_id\":\""
                + ACME_BEN + "\",\"due_date\":\"2026-12-01\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("To do"))
                .andExpect(jsonPath("$.title").value("Verification persistence check"))
                .andExpect(jsonPath("$.owner_member_id").value(ACME_BEN))
                .andExpect(jsonPath("$.due_date").value("2026-12-01"))
                .andExpect(jsonPath("$.created_by_member_id").value(ACME_ANA))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.task_id");
        writeAndForget();

        // (a) A later request from a new session of a different user (Vera, Viewer).
        MockHttpSession vera = signIn("vera@example.com");
        mvc.perform(get(taskPath(WEBSITE, id)).session(vera))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task_id").value(id))
                .andExpect(jsonPath("$.project_id").value(WEBSITE))
                .andExpect(jsonPath("$.title").value("Verification persistence check"))
                .andExpect(jsonPath("$.owner_member_id").value(ACME_BEN))
                .andExpect(jsonPath("$.owner_name").value("Ben Demo"))
                .andExpect(jsonPath("$.due_date").value("2026-12-01"))
                .andExpect(jsonPath("$.status").value("To do"))
                .andExpect(jsonPath("$.created_by_member_id").value(ACME_ANA));

        // (b) In the project's task list, and "To do" went up by one.
        listTasks(vera, WEBSITE).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.items[?(@.task_id == '" + id + "')].title")
                        .value(contains("Verification persistence check")))
                .andExpect(jsonPath("$.status_counts[0].status").value("To do"))
                .andExpect(jsonPath("$.status_counts[0].count").value(2));

        // (c) Exactly one stored row with these values, created by Ana's member id.
        assertThat(count("SELECT count(*) FROM task WHERE task_id = ?::uuid", id)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM task WHERE task_id = ?::uuid AND project_id = ?::uuid "
                + "AND title = 'Verification persistence check' AND owner_member_id = ?::uuid "
                + "AND due_date = DATE '2026-12-01' AND status = 'TO_DO' AND created_by_member_id = ?::uuid",
                id, WEBSITE, ACME_BEN, ACME_ANA)).isEqualTo(1);
    }

    // --- Case 04: validation of titles and comment bodies -----------------------------------------------------------

    @Test
    @DisplayName("Case 04 · AC-10.2, AC-10.5, FR-09 · createTask refuses empty, blank and out-of-range titles with "
            + "422 on title and saves nothing")
    void case04CreateRefusesInvalidTitles() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        long before = count("SELECT count(*) FROM task");
        String[] invalid = {"", "     ", "\\t \\n ", "  ab  ", "a".repeat(151), "  " + "a".repeat(151) + "  "};
        for (String title : invalid) {
            createTask(ana, WEBSITE, titled(title))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("validation_failed"))
                    .andExpect(jsonPath("$.details[0].field").value("title"));
            discardRefusedRequest();
        }
        assertThat(count("SELECT count(*) FROM task")).isEqualTo(before);
        assertThat(count("SELECT count(*) FROM task WHERE project_id = ?::uuid", WEBSITE)).isEqualTo(3);
    }

    @Test
    @DisplayName("Case 04 · AC-10.5, FR-09 · createTask accepts titles of exactly 3 and 150 characters after "
            + "trimming and stores them trimmed")
    void case04CreateAcceptsBoundaryTitlesTrimmed() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        String three = JsonPath.read(createTask(ana, WEBSITE, titled("  abc  "))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("abc"))
                .andReturn().getResponse().getContentAsString(), "$.task_id");
        String max = "b".repeat(150);
        String hundredFifty = JsonPath.read(createTask(ana, WEBSITE, titled("\\t " + max + " \\n"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value(max))
                .andReturn().getResponse().getContentAsString(), "$.task_id");
        writeAndForget();
        assertThat(storedTitle(three)).isEqualTo("abc");
        assertThat(storedTitle(hundredFifty)).isEqualTo(max);
    }

    @Test
    @DisplayName("Case 04 · AC-25.1, AC-25.2, AC-10.5, FR-09 · updateTask refuses empty, blank and out-of-range "
            + "titles with 422 on title and keeps the stored title")
    void case04UpdateRefusesInvalidTitles() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        long before = count("SELECT count(*) FROM task");
        String[] invalid = {"", "     ", "\\t \\n ", "  ab  ", "a".repeat(151), "  " + "a".repeat(151) + "  "};
        for (String title : invalid) {
            updateTask(ana, WEBSITE, T_LAYOUT, "{\"title\":\"" + title + "\"}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("validation_failed"))
                    .andExpect(jsonPath("$.details[0].field").value("title"));
            discardRefusedRequest();
            assertThat(storedTitle(T_LAYOUT)).isEqualTo("Approve page layout");
        }
        assertThat(count("SELECT count(*) FROM task")).isEqualTo(before);
        mvc.perform(get(taskPath(WEBSITE, T_LAYOUT)).session(signIn("vera@example.com")))
                .andExpect(jsonPath("$.title").value("Approve page layout"));
    }

    @Test
    @DisplayName("Case 04 · AC-10.5, AC-25.2, FR-09 · updateTask accepts titles of exactly 3 and 150 characters "
            + "after trimming and stores them trimmed")
    void case04UpdateAcceptsBoundaryTitlesTrimmed() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        updateTask(ana, WEBSITE, T_LAYOUT, "{\"title\":\"  xyz  \"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("xyz"));
        writeAndForget();
        assertThat(storedTitle(T_LAYOUT)).isEqualTo("xyz");
        String max = "c".repeat(150);
        updateTask(ana, WEBSITE, T_LAYOUT, "{\"title\":\" " + max + "\\t\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(max));
        writeAndForget();
        assertThat(storedTitle(T_LAYOUT)).isEqualTo(max);
    }

    @Test
    @DisplayName("Case 04 · AC-15.2, EC-08, FR-16 · addTaskComment refuses empty, blank and 2001-character bodies "
            + "with 422 on body and adds nothing")
    void case04CommentRefusesInvalidBodies() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        long all = count("SELECT count(*) FROM comment");
        long onImages = count("SELECT count(*) FROM comment WHERE task_id = ?::uuid", T_IMAGES);
        String[] invalid = {"", "     ", "\\t \\n ", "\\t\\n\\t", "c".repeat(2001), "  " + "c".repeat(2001) + "  "};
        for (String body : invalid) {
            comment(ana, WEBSITE, T_IMAGES, body)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("validation_failed"))
                    .andExpect(jsonPath("$.details[0].field").value("body"));
            discardRefusedRequest();
        }
        assertThat(count("SELECT count(*) FROM comment")).isEqualTo(all);
        assertThat(count("SELECT count(*) FROM comment WHERE task_id = ?::uuid", T_IMAGES)).isEqualTo(onImages);
        mvc.perform(get(taskPath(WEBSITE, T_IMAGES) + "/comments").session(ana))
                .andExpect(jsonPath("$", hasSize((int) onImages)));
    }

    @Test
    @DisplayName("Case 04 · AC-15.2, FR-16 · addTaskComment accepts bodies of 1 and 2000 characters after trimming")
    void case04CommentAcceptsBoundaryBodies() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        String one = JsonPath.read(comment(ana, WEBSITE, T_LAYOUT, "  k  ").andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("k")).andReturn().getResponse().getContentAsString(), "$.comment_id");
        comment(ana, WEBSITE, T_LAYOUT, "d".repeat(2000)).andExpect(status().isCreated());
        writeAndForget();
        assertThat(jdbc.queryForObject("SELECT body FROM comment WHERE comment_id = ?::uuid", String.class, one))
                .isEqualTo("k");
        assertThat(count("SELECT count(*) FROM comment WHERE task_id = ?::uuid", T_LAYOUT)).isEqualTo(2);
    }

    // --- Case 05: status changes agree everywhere ---------------------------------------------------------------------

    @Test
    @DisplayName("Case 05 · AC-11.1, AC-17.2, FR-12, FR-15, FR-18 · the owner moves a task To do, In progress, Done "
            + "and the task, filtered list, status counts and progress agree")
    void case05StatusChangesAgreeEverywhere() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        // Ben (Team Member) creates a task he owns, so he can change its status (AC-11.1; PD-01 provisional).
        String id = JsonPath.read(createTask(ben, WEBSITE, "{\"title\":\"Verification status check\","
                + "\"owner_member_id\":\"" + ACME_BEN + "\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("To do"))
                .andReturn().getResponse().getContentAsString(), "$.task_id");
        writeAndForget();
        MockHttpSession vera = signIn("vera@example.com");
        listTasks(vera, WEBSITE, "status", "To do").andExpect(jsonPath("$.items[*].task_id", contains(T_LAYOUT, id)))
                .andExpect(jsonPath("$.status_counts[*].count", contains(2, 1, 1)));
        progress(vera, WEBSITE).andExpect(jsonPath("$[0].done").value(1)).andExpect(jsonPath("$[0].total").value(4));

        changeStatus(ben, id, "In progress").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("In progress"));
        writeAndForget();
        assertThat(jdbc.queryForObject("SELECT status FROM task WHERE task_id = ?::uuid", String.class, id))
                .isEqualTo("IN_PROGRESS");
        mvc.perform(get(taskPath(WEBSITE, id)).session(vera)).andExpect(jsonPath("$.status").value("In progress"));
        listTasks(vera, WEBSITE, "status", "In progress")
                .andExpect(jsonPath("$.items[*].task_id", contains(T_IMAGES, id)))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.status_counts[*].count", contains(1, 2, 1)));
        listTasks(vera, WEBSITE, "status", "To do").andExpect(jsonPath("$.items[*].task_id", contains(T_LAYOUT)));
        progress(vera, WEBSITE).andExpect(jsonPath("$[0].done").value(1)).andExpect(jsonPath("$[0].total").value(4));

        changeStatus(ben, id, "Done").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("Done"));
        writeAndForget();
        assertThat(jdbc.queryForObject("SELECT status FROM task WHERE task_id = ?::uuid", String.class, id))
                .isEqualTo("DONE");
        mvc.perform(get(taskPath(WEBSITE, id)).session(vera)).andExpect(jsonPath("$.status").value("Done"));
        listTasks(vera, WEBSITE, "status", "Done").andExpect(jsonPath("$.items[*].task_id", contains(T_PUBLISH, id)))
                .andExpect(jsonPath("$.status_counts[*].count", contains(1, 1, 2)));
        listTasks(vera, WEBSITE, "status", "In progress").andExpect(jsonPath("$.items[*].task_id", contains(T_IMAGES)));
        // AC-17.2: one more task Done, so "2 of 4 tasks done".
        progress(vera, WEBSITE).andExpect(jsonPath("$[0].project_id").value(WEBSITE))
                .andExpect(jsonPath("$[0].done").value(2)).andExpect(jsonPath("$[0].total").value(4));
    }

    // --- Case 06: no duplicate assignees ------------------------------------------------------------------------------

    @Test
    @DisplayName("Case 06 · AC-29.1, AC-29.3, AC-29.4, BR-22, FR-22 · duplicate assignees give 409 and save nothing; "
            + "removing one assignee keeps the other and the owner")
    void case06DuplicateAssigneesAreRefused() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        String rows = "SELECT count(*) FROM task_assignee WHERE task_id = ?::uuid";

        // The same member twice in one request (contract assignTask rule 3).
        assign(ana, WEBSITE, T_LAYOUT, ACME_BEN, ACME_BEN).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"))
                .andExpect(jsonPath("$.details[0].field").value("member_ids"));
        discardRefusedRequest();
        assertThat(count(rows, T_LAYOUT)).isZero();

        // Ben, then Ben again (AC-29.3).
        assign(ana, WEBSITE, T_LAYOUT, ACME_BEN).andExpect(status().isCreated());
        writeAndForget();
        assign(ana, WEBSITE, T_LAYOUT, ACME_BEN).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"))
                .andExpect(jsonPath("$.details[0].field").value("member_ids"));
        discardRefusedRequest();
        mvc.perform(get(taskPath(WEBSITE, T_LAYOUT) + "/assignees").session(ana))
                .andExpect(jsonPath("$[*].member_id", contains(ACME_BEN)));
        assertThat(count(rows, T_LAYOUT)).isEqualTo(1);
        assertThat(count(rows + " AND member_id = ?::uuid", T_LAYOUT, ACME_BEN)).isEqualTo(1);

        // Ben and Vera assigned, Ben removed: only Vera, and the owner is still Ana (AC-29.1, AC-29.4).
        assign(ana, WEBSITE, T_LAYOUT, ACME_VERA).andExpect(status().isCreated())
                .andExpect(jsonPath("$[*].member_id", contains(ACME_BEN, ACME_VERA)));
        unassign(ana, WEBSITE, T_LAYOUT, ACME_BEN).andExpect(status().isNoContent());
        writeAndForget();
        mvc.perform(get(taskPath(WEBSITE, T_LAYOUT) + "/assignees").session(signIn("vera@example.com")))
                .andExpect(jsonPath("$[*].member_id", contains(ACME_VERA)));
        assertThat(count(rows, T_LAYOUT)).isEqualTo(1);
        assertThat(count(rows + " AND member_id = ?::uuid", T_LAYOUT, ACME_VERA)).isEqualTo(1);
        mvc.perform(get(taskPath(WEBSITE, T_LAYOUT)).session(ana))
                .andExpect(jsonPath("$.owner_member_id").value(ACME_ANA));
        assertThat(count("SELECT count(*) FROM task WHERE task_id = ?::uuid AND owner_member_id = ?::uuid",
                T_LAYOUT, ACME_ANA)).isEqualTo(1);
    }

    // --- Case 07: comment author and date -----------------------------------------------------------------------------

    @Test
    @DisplayName("Case 07 · AC-15.1, AC-15.3, FR-16 · a comment by Ana is read by Vera with Ana as author and a "
            + "server-set date, and stored with Ana's member id")
    void case07CommentAuthorAndDateComeFromTheServer() throws Exception {
        Instant before = Instant.now().minusSeconds(1);
        MockHttpSession ana = signIn("ana@example.com");
        String body = comment(ana, WEBSITE, T_IMAGES, "Verification comment from Ana.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author_member_id").value(ACME_ANA))
                .andExpect(jsonPath("$.author_name").value("Ana Demo"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.comment_id");
        Instant created = Instant.parse(JsonPath.read(body, "$.created_at"));
        assertThat(created).isAfter(before).isBefore(Instant.now().plusSeconds(1));
        writeAndForget();

        String listed = mvc.perform(get(taskPath(WEBSITE, T_IMAGES) + "/comments").session(signIn("vera@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[2].comment_id").value(id))
                .andExpect(jsonPath("$[2].author_member_id").value(ACME_ANA))
                .andExpect(jsonPath("$[2].author_name").value("Ana Demo"))
                .andExpect(jsonPath("$[2].body").value("Verification comment from Ana."))
                .andReturn().getResponse().getContentAsString();
        assertThat(Instant.parse(JsonPath.<String>read(listed, "$[2].created_at")))
                .isAfter(before).isBefore(Instant.now().plusSeconds(1));
        assertThat(jdbc.queryForObject("SELECT author_member_id::text FROM comment WHERE comment_id = ?::uuid",
                String.class, id)).isEqualTo(ACME_ANA);

        // A client-sent author is refused, and nothing is stored (AC-15.3).
        long count = count("SELECT count(*) FROM comment WHERE task_id = ?::uuid", T_IMAGES);
        mvc.perform(post(taskPath(WEBSITE, T_IMAGES) + "/comments").session(ana).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Spoofed\",\"author_member_id\":\"" + ACME_BEN + "\"}"))
                .andExpect(status().isUnprocessableEntity());
        discardRefusedRequest();
        assertThat(count("SELECT count(*) FROM comment WHERE task_id = ?::uuid", T_IMAGES)).isEqualTo(count);
    }

    // --- Case 08: authorization by changing identifiers -------------------------------------------------------------

    @Test
    @DisplayName("Case 08 · NFR-01, SP-02, SP-03, FR-05, API-A3 · a Northwind Project Manager using Acme ids gets 404 "
            + "on every task, project, comment and assignee operation and changes nothing")
    void case08OtherWorkspaceIdsAreNotFoundAndChangeNothing() throws Exception {
        // An assignment to aim at, created by an allowed user before the snapshot.
        assign(signIn("ana@example.com"), WEBSITE, T_LAYOUT, ACME_VERA).andExpect(status().isCreated());
        writeAndForget();
        Map<String, List<Map<String, Object>>> before = snapshot();

        MockHttpSession dan = signIn("dan@example.com");
        String task = taskPath(WEBSITE, T_LAYOUT);
        String throughLab = taskPath(LAB, T_LAYOUT);
        List<ResultActions> refused = List.of(
                mvc.perform(get(task).session(dan)),
                mvc.perform(patch(task).session(dan).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Taken over\"}")),
                mvc.perform(delete(task).session(dan).with(csrf())),
                mvc.perform(get(throughLab).session(dan)),
                mvc.perform(patch(throughLab).session(dan).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Taken over\"}")),
                mvc.perform(delete(throughLab).session(dan).with(csrf())),
                mvc.perform(get(throughLab + "/comments").session(dan)),
                mvc.perform(get(throughLab + "/assignees").session(dan)),
                mvc.perform(get("/projects/" + WEBSITE + "/tasks").session(dan)),
                mvc.perform(post("/projects/" + WEBSITE + "/tasks").session(dan).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(titled("Taken over"))),
                mvc.perform(get("/projects/" + WEBSITE).session(dan)),
                mvc.perform(patch("/projects/" + WEBSITE).session(dan).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Taken over\"}")),
                mvc.perform(delete("/projects/" + WEBSITE).session(dan).with(csrf())),
                mvc.perform(get(task + "/comments").session(dan)),
                mvc.perform(post(task + "/comments").session(dan).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Taken over\"}")),
                mvc.perform(get(task + "/assignees").session(dan)),
                mvc.perform(post(task + "/assignees").session(dan).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"member_ids\":[\"" + ACME_BEN + "\"]}")),
                mvc.perform(delete(task + "/assignees/" + ACME_VERA).session(dan).with(csrf())),
                mvc.perform(get(task + "/eligible-assignees").session(dan)),
                mvc.perform(patch(task).session(dan).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"Done\"}")),
                mvc.perform(patch(throughLab).session(dan).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"Done\"}")));
        for (ResultActions result : refused) {
            result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("not_found"));
        }
        discardRefusedRequest();

        // getProjectProgress leaves out projects the caller cannot see (contract: 200, no entry), it does not 404.
        progress(dan, WEBSITE).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("Case 08 · FR-04, FR-05, BR-12, PD-01 and PD-17 provisional · inside Acme a Team Member editing or "
            + "deleting a task he neither owns nor created, and a Viewer changing data, get 403 and change nothing")
    void case08RefusedActionsInsideTheWorkspaceChangeNothing() throws Exception {
        assign(signIn("ana@example.com"), WEBSITE, T_LAYOUT, ACME_VERA).andExpect(status().isCreated());
        writeAndForget();
        Map<String, List<Map<String, Object>>> before = snapshot();

        // Ben (Team Member): "Approve page layout" is owned and created by Ana.
        MockHttpSession ben = signIn("ben@example.com");
        updateTask(ben, WEBSITE, T_LAYOUT, "{\"title\":\"Changed by Ben\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
        discardRefusedRequest();
        updateTask(ben, WEBSITE, T_LAYOUT, "{\"status\":\"Done\"}").andExpect(status().isForbidden());
        discardRefusedRequest();
        deleteTask(ben, WEBSITE, T_LAYOUT).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
        discardRefusedRequest();
        unassign(ben, WEBSITE, T_LAYOUT, ACME_VERA).andExpect(status().isForbidden());
        discardRefusedRequest();

        // Vera (Viewer).
        MockHttpSession vera = signIn("vera@example.com");
        changeStatus(vera, T_LAYOUT, "Done").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
        discardRefusedRequest();
        updateTask(vera, WEBSITE, T_LAYOUT, "{\"status\":\"In progress\"}").andExpect(status().isForbidden());
        discardRefusedRequest();
        comment(vera, WEBSITE, T_LAYOUT, "Viewer comment").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
        discardRefusedRequest();
        createTask(vera, WEBSITE, "{\"title\":\"Viewer task\",\"owner_member_id\":\"" + ACME_VERA + "\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
        discardRefusedRequest();
        assign(vera, WEBSITE, T_LAYOUT, ACME_BEN).andExpect(status().isForbidden());
        discardRefusedRequest();

        assertThat(snapshot()).isEqualTo(before);
        mvc.perform(get(taskPath(WEBSITE, T_LAYOUT)).session(signIn("ana@example.com")))
                .andExpect(jsonPath("$.title").value("Approve page layout"))
                .andExpect(jsonPath("$.status").value("To do"))
                .andExpect(jsonPath("$.owner_member_id").value(ACME_ANA));
    }
}
