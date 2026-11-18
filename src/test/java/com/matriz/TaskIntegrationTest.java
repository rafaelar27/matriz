package com.matriz;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matriz.seed.DemoDataSeeder;

/**
 * Integration tests for task creation and reading (INC-09, number provisional; US-10; FR-09 to FR-13; NFR-01,
 * NFR-03; test-plan TC-01, TC-02, TV-01, TG-01, TA-01, TA-02, TA-03).
 *
 * <p>Unlike the other API tests, these use real HTTP on a random port, a real sign-in ({@code GET /auth/csrf} then
 * {@code POST /auth/sign-in}), the real session cookie and the real {@code X-XSRF-TOKEN} header, with nothing
 * mocked. Data is committed (no test-level rollback) in the matriz_test database. The demo data is loaded before each
 * test; every task a test creates is recorded and deleted afterwards, and the demo baseline (5 tasks, 3 in
 * "Website redesign") is asserted before and after each test. Expected results come from openapi.yaml,
 * docs/user-stories.md, docs/constraints.md, docs/database.md and docs/test-plan.md; message texts are not asserted.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TaskIntegrationTest {

    private static final String PASSWORD = ApiTestSupport.TEST_PASSWORD;
    private static final String WEBSITE = ApiTestSupport.WEBSITE;
    private static final String T_IMAGES = ApiTestSupport.T_IMAGES;
    private static final String BEN = ApiTestSupport.ACME_BEN;
    private static final String ANA = ApiTestSupport.ACME_ANA;
    private static final String UNKNOWN_TASK = "e0000000-0000-0000-0000-000000000999";
    /** Fictitious future due date (Q-IT3). */
    private static final String DUE = "2027-03-15";
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    private final List<String> createdTaskIds = new ArrayList<>();

    /** One HTTP client with its own cookie jar, that is one browser session. */
    private final class Client {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

        String xsrf() {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(c -> c.getName().equals("XSRF-TOKEN")).map(c -> c.getValue()).findFirst().orElse(null);
        }

        Resp call(String method, String path, String body) throws Exception {
            return send(method, path, body, true);
        }

        /** Same as {@link #call} but never sends the X-XSRF-TOKEN header (the cookie jar is still used). */
        Resp callWithoutCsrfHeader(String method, String path, String body) throws Exception {
            return send(method, path, body, false);
        }

        private Resp send(String method, String path, String body, boolean csrfHeader) throws Exception {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
            if (body == null) {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
            }
            if (csrfHeader && !method.equals("GET")) {
                String token = xsrf();
                if (token != null) {
                    b.header("X-XSRF-TOKEN", token);
                }
            }
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Resp(r.statusCode(), r.body(), r.headers().firstValue("Location").orElse(null));
        }

        /** Real sign-in: obtains the CSRF cookie, then posts the credentials with the CSRF header. */
        Client signIn(String email) throws Exception {
            call("GET", "/auth/csrf", null);
            Resp r = call("POST", "/auth/sign-in",
                    "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}");
            assertThat(r.status).as("sign-in " + email).isEqualTo(200);
            return this;
        }
    }

    private record Resp(int status, String body, String location) {
        JsonNode json() throws Exception {
            return JSON.readTree(body);
        }
    }

    private Client signedIn(String email) throws Exception {
        return new Client().signIn(email);
    }

    private static String taskBody(String title) {
        return "{\"title\":" + quote(title) + ",\"owner_member_id\":\"" + BEN + "\"}";
    }

    private static String quote(String s) {
        return "\"" + s + "\"";
    }

    private int totalTasks() {
        return jdbc.queryForObject("SELECT count(*) FROM task", Integer.class);
    }

    private int websiteTasks() {
        return jdbc.queryForObject("SELECT count(*) FROM task WHERE project_id = ?::uuid", Integer.class, WEBSITE);
    }

    private void assertBaseline() {
        assertThat(totalTasks()).as("tasks in matriz_test").isEqualTo(5);
        assertThat(websiteTasks()).as("tasks in Website redesign").isEqualTo(3);
    }

    @BeforeEach
    void loadDemoDataAndCheckBaseline() {
        new DemoDataSeeder(jdbc, tx, PASSWORD).run();
        assertBaseline();
    }

    @AfterEach
    void removeCreatedTasksAndCheckBaseline() {
        for (String id : createdTaskIds) {
            jdbc.update("DELETE FROM task WHERE task_id = ?::uuid", id);
        }
        createdTaskIds.clear();
        assertBaseline();
    }

    private JsonNode listWebsite(Client c) throws Exception {
        Resp r = c.call("GET", "/projects/" + WEBSITE + "/tasks", null);
        assertThat(r.status).isEqualTo(200);
        return r.json();
    }

    private static int statusCount(JsonNode page, String status) {
        for (JsonNode n : page.get("status_counts")) {
            if (n.get("status").asText().equals(status)) {
                return n.get("count").asInt();
            }
        }
        throw new AssertionError("no status_counts entry for " + status);
    }

    /** {total, done} of the project from GET /progress (FR-18). */
    private int[] progress(Client c, String project) throws Exception {
        Resp r = c.call("GET", "/progress?project_id=" + project, null);
        assertThat(r.status).isEqualTo(200);
        for (JsonNode n : r.json()) {
            if (n.get("project_id").asText().equals(project)) {
                return new int[] {n.get("total").asInt(), n.get("done").asInt()};
            }
        }
        throw new AssertionError("no progress entry for " + project);
    }

    private void assertError(Resp r, int status, String code) throws Exception {
        assertThat(r.status).isEqualTo(status);
        assertThat(r.json().get("code").asText()).isEqualTo(code);
    }

    @Test
    @DisplayName("TC-01/TC-02 · AC-10.1 · a Project Manager creates a task with every field and it is read back (FR-09 to FR-13, NFR-03)")
    void createTaskWithEveryFieldAndReadItBack() throws Exception {
        Client ana = signedIn("ana@example.com");
        JsonNode before = listWebsite(ana);
        assertThat(before.get("total").asInt()).isEqualTo(3);
        int todoBefore = statusCount(before, "To do");
        int[] progressBefore = progress(ana, WEBSITE);

        Resp created = ana.call("POST", "/projects/" + WEBSITE + "/tasks",
                "{\"title\":\"Draft homepage text\","
                        + "\"description\":\"First draft of the homepage copy for review.\","
                        + "\"owner_member_id\":\"" + BEN + "\",\"due_date\":\"" + DUE + "\"}");
        JsonNode body = created.status == 201 ? created.json() : null;
        if (body != null && body.hasNonNull("task_id")) {
            createdTaskIds.add(body.get("task_id").asText());
        }

        // TC-01: 201 and the documented body.
        assertThat(created.status).isEqualTo(201);
        String taskId = body.get("task_id").asText();
        assertThat(taskId).isNotBlank();
        // openapi createTask 201: Location is the path of the new task, project-scoped (Q-11, S-01).
        String expectedLocation = "/projects/" + WEBSITE + "/tasks/" + taskId;
        assertThat(created.location).as("Location header equals the exact task path (openapi createTask)")
                .isEqualTo(expectedLocation);
        // Follow the advertised link with the same session: it must open the same task.
        Resp followed = ana.call("GET", created.location, null);
        assertThat(followed.status).isEqualTo(200);
        assertThat(followed.json().get("task_id").asText()).isEqualTo(taskId);
        assertThat(body.get("project_id").asText()).isEqualTo(WEBSITE);
        assertThat(body.get("title").asText()).isEqualTo("Draft homepage text");
        assertThat(body.get("description").asText()).isEqualTo("First draft of the homepage copy for review.");
        assertThat(body.get("owner_member_id").asText()).isEqualTo(BEN);
        assertThat(body.get("owner_name").asText()).isEqualTo("Ben Demo");
        assertThat(body.get("due_date").asText()).isEqualTo(DUE);
        assertThat(body.get("status").asText()).isEqualTo("To do");
        assertThat(body.get("created_by_member_id").asText()).isEqualTo(ANA);

        // TC-02 (a): the row is committed, seen on a fresh autocommit connection (status checked through the API).
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT project_id::text AS project_id, title, description, owner_member_id::text AS owner,"
                        + " due_date::text AS due, created_by_member_id::text AS creator"
                        + " FROM task WHERE task_id = ?::uuid", taskId);
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("project_id")).isEqualTo(WEBSITE);
        assertThat(row.get("title")).isEqualTo("Draft homepage text");
        assertThat(row.get("description")).isEqualTo("First draft of the homepage copy for review.");
        assertThat(row.get("owner")).isEqualTo(BEN);
        assertThat(row.get("due")).isEqualTo(DUE);
        assertThat(row.get("creator")).isEqualTo(ANA);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM task WHERE title = 'Draft homepage text'",
                Integer.class)).isEqualTo(1);

        // TC-02 (b): a new session for Vera (Viewer) reads the same fields.
        Resp read = signedIn("vera@example.com").call("GET", "/projects/" + WEBSITE + "/tasks/" + taskId, null);
        assertThat(read.status).isEqualTo(200);
        JsonNode got = read.json();
        assertThat(got.get("task_id").asText()).isEqualTo(taskId);
        assertThat(got.get("project_id").asText()).isEqualTo(WEBSITE);
        assertThat(got.get("title").asText()).isEqualTo("Draft homepage text");
        assertThat(got.get("description").asText()).isEqualTo("First draft of the homepage copy for review.");
        assertThat(got.get("owner_member_id").asText()).isEqualTo(BEN);
        assertThat(got.get("owner_name").asText()).isEqualTo("Ben Demo");
        assertThat(got.get("due_date").asText()).isEqualTo(DUE);
        assertThat(got.get("status").asText()).isEqualTo("To do");
        assertThat(got.get("created_by_member_id").asText()).isEqualTo(ANA);

        // TC-02 (c): the list total is 3 -> 4 and To do +1.
        JsonNode after = listWebsite(signedIn("ben@example.com"));
        assertThat(after.get("total").asInt()).isEqualTo(4);
        assertThat(statusCount(after, "To do")).isEqualTo(todoBefore + 1);

        // TC-02 (d): progress total +1, done unchanged (FR-18).
        int[] progressAfter = progress(ana, WEBSITE);
        assertThat(progressAfter[0]).as("progress total").isEqualTo(progressBefore[0] + 1);
        assertThat(progressAfter[1]).as("progress done").isEqualTo(progressBefore[1]);
    }

    @Test
    @DisplayName("TV-01 · AC-10.2, AC-10.5, BR-10, A8 · an invalid title is refused with 422 validation_failed and nothing is created")
    void invalidTitleIsRefusedAndNothingIsCreated() throws Exception {
        Client ana = signedIn("ana@example.com");
        String[] bodies = {
            taskBody("   "),
            taskBody("x".repeat(151)),
            taskBody(""),
            "{\"owner_member_id\":\"" + BEN + "\"}",
            taskBody("ab"),
            taskBody("  ab  "),
            "{\"title\":null,\"owner_member_id\":\"" + BEN + "\"}", // A8
        };
        for (String body : bodies) {
            Resp r = ana.call("POST", "/projects/" + WEBSITE + "/tasks", body);
            if (r.status == 201) {
                createdTaskIds.add(r.json().get("task_id").asText());
            }
            assertError(r, 422, "validation_failed");
            List<String> fields = new ArrayList<>();
            for (JsonNode d : r.json().get("details")) {
                fields.add(d.get("field").asText());
            }
            assertThat(fields).as("details[].field for " + abbreviate(body)).contains("title");
            assertThat(totalTasks()).isEqualTo(5);
            assertThat(websiteTasks()).isEqualTo(3);
            assertThat(listWebsite(ana).get("total").asInt()).isEqualTo(3);
        }
    }

    private static String abbreviate(String s) {
        return s.length() > 60 ? s.substring(0, 60) + "..." : s;
    }

    @Test
    @DisplayName("TG-01 · an unknown task in an accessible project gives 404 not_found")
    void unknownTaskInAccessibleProjectIsNotFound() throws Exception {
        Resp r = signedIn("ana@example.com").call("GET", "/projects/" + WEBSITE + "/tasks/" + UNKNOWN_TASK, null);
        assertError(r, 404, "not_found");
    }

    @Test
    @DisplayName("TA-03 · NFR-01, SP-02, API-A3 · a member of another workspace gets the same 404 as for an unknown id and sees no task data")
    void memberOfAnotherWorkspaceCannotReadOrCreate() throws Exception {
        Client ana = signedIn("ana@example.com");
        Resp unknown = ana.call("GET", "/projects/" + WEBSITE + "/tasks/" + UNKNOWN_TASK, null);
        assertError(unknown, 404, "not_found");

        Client dan = signedIn("dan@example.com");
        Resp list = dan.call("GET", "/projects/" + WEBSITE + "/tasks", null);
        Resp one = dan.call("GET", "/projects/" + WEBSITE + "/tasks/" + T_IMAGES, null);
        for (Resp r : new Resp[] {list, one}) {
            assertThat(r.status).isEqualTo(unknown.status);
            assertError(r, 404, "not_found");
            JsonNode j = r.json();
            assertThat(j.has("items")).isFalse();
            assertThat(j.has("title")).isFalse();
            assertThat(j.has("task_id")).isFalse();
            assertThat(r.body).doesNotContain("Review homepage images");
        }

        Resp create = dan.call("POST", "/projects/" + WEBSITE + "/tasks", taskBody("Outsider task"));
        if (create.status == 201) {
            createdTaskIds.add(create.json().get("task_id").asText());
        }
        assertError(create, 404, "not_found");
        assertThat(totalTasks()).isEqualTo(5);
        assertThat(websiteTasks()).isEqualTo(3);
    }

    @Test
    @DisplayName("TA-01 · FR-01, SP-01 · without a session createTask and getTask give 401 not_authenticated and create nothing")
    void withoutSessionRequestsAreRefused() throws Exception {
        Client anonymous = new Client();
        anonymous.call("GET", "/auth/csrf", null); // CSRF cookie only, no sign-in, so no session

        Resp create = anonymous.call("POST", "/projects/" + WEBSITE + "/tasks", taskBody("Anonymous task"));
        if (create.status == 201) {
            createdTaskIds.add(create.json().get("task_id").asText());
        }
        assertError(create, 401, "not_authenticated");

        Resp get = anonymous.call("GET", "/projects/" + WEBSITE + "/tasks/" + T_IMAGES, null);
        assertError(get, 401, "not_authenticated");

        assertThat(totalTasks()).isEqualTo(5);
        assertThat(websiteTasks()).isEqualTo(3);
    }

    @Test
    @DisplayName("TA-02 · openapi CsrfHeader · POST, PATCH and DELETE without X-XSRF-TOKEN give 403 csrf_invalid and change nothing")
    void withoutCsrfHeaderChangesAreRefused() throws Exception {
        Client ana = signedIn("ana@example.com");
        // A task this test creates itself (with the header), so that PATCH and DELETE have a target.
        Resp made = ana.call("POST", "/projects/" + WEBSITE + "/tasks", taskBody("Csrf target task"));
        assertThat(made.status).isEqualTo(201);
        String id = made.json().get("task_id").asText();
        createdTaskIds.add(id);
        assertThat(totalTasks()).isEqualTo(6);

        Resp post = ana.callWithoutCsrfHeader("POST", "/projects/" + WEBSITE + "/tasks", taskBody("No header task"));
        if (post.status == 201) {
            createdTaskIds.add(post.json().get("task_id").asText());
        }
        assertError(post, 403, "csrf_invalid");
        assertThat(totalTasks()).as("no task created without the header").isEqualTo(6);

        Resp patch = ana.callWithoutCsrfHeader("PATCH", "/projects/" + WEBSITE + "/tasks/" + id,
                "{\"title\":\"Changed without header\"}");
        assertError(patch, 403, "csrf_invalid");
        assertThat(jdbc.queryForObject("SELECT title FROM task WHERE task_id = ?::uuid", String.class, id))
                .isEqualTo("Csrf target task");

        Resp delete = ana.callWithoutCsrfHeader("DELETE", "/projects/" + WEBSITE + "/tasks/" + id, null);
        assertError(delete, 403, "csrf_invalid");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM task WHERE task_id = ?::uuid", Integer.class, id))
                .as("task still exists").isEqualTo(1);
        assertThat(totalTasks()).isEqualTo(6);
    }
}
