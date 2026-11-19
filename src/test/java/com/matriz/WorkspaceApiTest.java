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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/**
 * createWorkspace through direct API requests (INC-12; US-02; FR-02, BR-01, SP-01, SP-07, NFR-01, NFR-03).
 * US-02 has no numbered acceptance criteria in docs/user-stories.md, so tests carry FR, BR, SP and NFR ids.
 * The creator, a Workspace Administrator, manages projects of the new workspace (decided by the human 2026-11-19).
 */
class WorkspaceApiTest extends ApiTestSupport {

    @PersistenceContext
    private EntityManager entityManager;

    private ResultActions create(MockHttpSession s, String json) throws Exception {
        return mvc.perform(post("/workspaces").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String named(String name) {
        return "{\"name\":\"" + name + "\"}";
    }

    private long count(String sql, Object... args) {
        entityManager.flush(); // the service saves through JPA; write pending changes before SQL reads
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long workspaces() {
        return count("SELECT count(*) FROM workspace");
    }

    private long members() {
        return count("SELECT count(*) FROM member");
    }

    private String accountId(MockHttpSession s) throws Exception {
        String body = mvc.perform(get("/auth/me").session(s)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.account_id");
    }

    private void assertRefusedAndNothingSaved(MockHttpSession s, String json, String field) throws Exception {
        long w = workspaces();
        long m = members();
        create(s, json).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.details[0].field").value(field));
        assertThat(workspaces()).isEqualTo(w);
        assertThat(members()).isEqualTo(m);
    }

    // --- Success and persistence ---------------------------------------------------------------------------------

    @Test
    @DisplayName("FR-02, US-02, BR-01 · a signed-in account creates a workspace, gets 201 with its first membership and sees it in /auth/me")
    void createsWorkspaceAndListsMembership() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        MvcResult result = create(ben, named("Harbor Works"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Harbor Works"))
                .andExpect(jsonPath("$.role").value("Workspace Administrator"))
                .andExpect(jsonPath("$.workspace_id").isNotEmpty())
                .andExpect(jsonPath("$.member_id").isNotEmpty())
                .andReturn();
        assertThat(result.getResponse().getHeader("Location")).isNull();
        String body = result.getResponse().getContentAsString();
        String workspaceId = JsonPath.read(body, "$.workspace_id");
        String memberId = JsonPath.read(body, "$.member_id");

        mvc.perform(get("/auth/me").session(ben))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberships[?(@.workspace_id=='" + workspaceId + "')].member_id")
                        .value(memberId))
                .andExpect(jsonPath("$.memberships[?(@.workspace_id=='" + workspaceId + "')].workspace_name")
                        .value("Harbor Works"))
                .andExpect(jsonPath("$.memberships[?(@.workspace_id=='" + workspaceId + "')].role")
                        .value("Workspace Administrator"));
        // Persistence: a new session of the same account reads it back.
        mvc.perform(get("/auth/me").session(signIn("ben@example.com")))
                .andExpect(jsonPath("$.memberships[?(@.workspace_id=='" + workspaceId + "')]", hasSize(1)));
    }

    @Test
    @DisplayName("FR-02, SP-07, NFR-03 · the workspace and its first member are saved together; the member is the session account, active, Workspace Administrator")
    void workspaceAndMemberSavedTogether() throws Exception {
        MockHttpSession ana = signIn("ana@example.com"); // already belongs to workspaces
        long w = workspaces();
        long m = members();
        String body = create(ana, named("  Second Space  ")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Second Space"))
                .andReturn().getResponse().getContentAsString();
        String workspaceId = JsonPath.read(body, "$.workspace_id");
        String memberId = JsonPath.read(body, "$.member_id");

        assertThat(workspaces()).isEqualTo(w + 1);
        assertThat(members()).isEqualTo(m + 1);
        assertThat(count("SELECT count(*) FROM member WHERE workspace_id = ?::uuid", workspaceId)).isEqualTo(1);
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT name FROM workspace WHERE workspace_id = ?::uuid", String.class,
                workspaceId)).isEqualTo("Second Space");
        assertThat(jdbc.queryForObject("SELECT account_id::text FROM member WHERE member_id = ?::uuid", String.class,
                memberId)).isEqualTo(accountId(ana));
        assertThat(jdbc.queryForObject("SELECT role FROM member WHERE member_id = ?::uuid", String.class, memberId))
                .isEqualTo("WORKSPACE_ADMINISTRATOR");
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE member_id = ?::uuid", String.class, memberId))
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("FR-02, BR-01 · any account can create, also a Viewer and an account of another workspace")
    void anyRoleCanCreate() throws Exception {
        for (String email : new String[] {"vera@example.com", "carla@example.com", "ana@example.com"}) {
            create(signIn(email), named("Own space")).andExpect(status().isCreated());
        }
    }

    @Test
    @DisplayName("AC-INC12.4, FR-02, BR-01 · an account whose memberships are all deactivated can create a workspace, and the new membership is active")
    void accountWithOnlyDeactivatedMembershipsCanCreate() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        String account = accountId(ana);
        jdbc.update("UPDATE member SET status = 'DEACTIVATED' WHERE account_id = ?::uuid", account);
        assertThat(count("SELECT count(*) FROM member WHERE account_id = ?::uuid AND status = 'ACTIVE'", account))
                .isZero();

        String memberId = JsonPath.read(create(ana, named("Fresh start")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("Workspace Administrator"))
                .andReturn().getResponse().getContentAsString(), "$.member_id");

        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE member_id = ?::uuid", String.class, memberId))
                .isEqualTo("ACTIVE");
        assertThat(count("SELECT count(*) FROM member WHERE account_id = ?::uuid AND status = 'ACTIVE'", account))
                .isEqualTo(1);
    }

    // --- Name rule -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("FR-02 · name boundaries: 2 characters refused, 3 and 100 accepted, 101 refused (422 on name)")
    void nameLengthBoundaries() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        assertRefusedAndNothingSaved(ben, named("ab"), "name");
        create(ben, named("abc")).andExpect(status().isCreated());
        create(ben, named("n".repeat(100))).andExpect(status().isCreated());
        assertRefusedAndNothingSaved(ben, named("n".repeat(101)), "name");
    }

    @Test
    @DisplayName("FR-02, BR-01 · decided: a JSON number or boolean name is not refused; it is saved as text")
    void numericAndBooleanNamesAreAccepted() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        create(ben, "{\"name\":12345}").andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("12345"));
        create(ben, "{\"name\":true}").andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("true"));
        entityManager.flush();
        assertThat(count("SELECT count(*) FROM workspace WHERE name = '12345'")).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-02, BR-01 · decided: no limit on the number of workspaces one account creates")
    void oneAccountCanCreateManyWorkspaces() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        long w = workspaces();
        long m = members();
        for (int i = 1; i <= 5; i++) {
            create(ben, named("Many spaces " + i)).andExpect(status().isCreated());
        }
        assertThat(workspaces()).isEqualTo(w + 5);
        assertThat(members()).isEqualTo(m + 5);
    }

    @Test
    @DisplayName("FR-02 · PROBE (no assertion on status): a name containing a NUL character; prints status, body and rows saved")
    void probeNulCharacterInName() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        long w = workspaces();
        String result;
        try {
            var r = create(ben, "{\"name\":\"ab\\u0000cd\"}").andReturn().getResponse();
            result = r.getStatus() + " " + r.getContentAsString();
        } catch (Exception e) {
            result = "exception " + e;
        }
        String saved;
        try {
            saved = String.valueOf(workspaces() - w);
        } catch (Exception e) {
            saved = "not countable (transaction aborted): " + e.getClass().getSimpleName();
        }
        System.out.println("PROBE NUL name: " + result + " | workspaces saved: " + saved);
    }

    @Test
    @DisplayName("FR-02 · whitespace-only, empty, null and missing name are refused 422 on name, nothing saved")
    void blankNullAndMissingNameRefused() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        assertRefusedAndNothingSaved(ben, named("   "), "name");
        assertRefusedAndNothingSaved(ben, named(""), "name");
        assertRefusedAndNothingSaved(ben, "{\"name\":null}", "name");
        assertRefusedAndNothingSaved(ben, "{}", "name");
    }

    @Test
    @DisplayName("FR-02 · the name is trimmed before the length check and before saving")
    void nameIsTrimmed() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        assertRefusedAndNothingSaved(ben, named("  ab  "), "name");
        create(ben, named("  abc  ")).andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("abc"));
        create(ben, named("  " + "n".repeat(100) + "  ")).andExpect(status().isCreated());
        assertThat(count("SELECT count(*) FROM workspace WHERE name = 'abc'")).isEqualTo(1);
    }

    @Test
    @DisplayName("BR-01, FR-02 · duplicate workspace names are allowed, also an existing name and twice by the same account")
    void duplicateNamesAllowed() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        long w = workspaces();
        create(ben, named("Acme Studio")).andExpect(status().isCreated());
        create(ben, named("Twin name")).andExpect(status().isCreated());
        create(ben, named("Twin name")).andExpect(status().isCreated());
        assertThat(workspaces()).isEqualTo(w + 3);
    }

    // --- Refused fields ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("SP-01, SP-07 · workspace_id, role, account_id, member_id and unknown fields are refused 422, nothing saved")
    void identityRoleAndUnknownFieldsRefused() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        String other = "\"c0000000-0000-0000-0000-000000000005\"";
        assertRefusedAndNothingSaved(ben, "{\"name\":\"Spoofed\",\"workspace_id\":" + other + "}", "workspace_id");
        assertRefusedAndNothingSaved(ben, "{\"name\":\"Spoofed\",\"role\":\"Viewer\"}", "role");
        assertRefusedAndNothingSaved(ben, "{\"name\":\"Spoofed\",\"account_id\":" + other + "}", "account_id");
        assertRefusedAndNothingSaved(ben, "{\"name\":\"Spoofed\",\"member_id\":" + other + "}", "member_id");
        assertRefusedAndNothingSaved(ben, "{\"name\":\"Spoofed\",\"is_archived\":true}", "is_archived");
    }

    @Test
    @DisplayName("FR-02 · an unparsable body is refused 400 and nothing is saved")
    void unparsableBodyRefused() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        long w = workspaces();
        long m = members();
        create(ben, "{\"name\":").andExpect(status().isBadRequest());
        create(ben, "not json").andExpect(status().isBadRequest());
        assertThat(workspaces()).isEqualTo(w);
        assertThat(members()).isEqualTo(m);
    }

    // --- Authentication and CSRF ---------------------------------------------------------------------------------

    @Test
    @DisplayName("SP-01 · createWorkspace without a session is refused 401, nothing saved")
    void noSessionIs401() throws Exception {
        long w = workspaces();
        mvc.perform(post("/workspaces").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(named("Harbor")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("not_authenticated"));
        assertThat(workspaces()).isEqualTo(w);
    }

    @Test
    @DisplayName("SP-01 · a session ended by sign-out (unknown to the server) is refused 401 session_expired")
    void endedSessionIs401() throws Exception {
        long w = workspaces();
        MockHttpSession ben = signIn("ben@example.com");
        mvc.perform(post("/auth/sign-out").session(ben).with(csrf())).andExpect(status().isNoContent());
        assertThat(ben.isInvalid()).isTrue();
        mvc.perform(post("/workspaces").with(csrf()).with(request -> {
                    request.setRequestedSessionId("ended-session-id");
                    request.setRequestedSessionIdValid(false);
                    return request;
                }).contentType(MediaType.APPLICATION_JSON).content(named("Harbor")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("session_expired"));
        assertThat(workspaces()).isEqualTo(w);
    }

    @Test
    @DisplayName("SP-01 · createWorkspace without a CSRF token is refused 403 csrf_invalid, nothing saved")
    void missingCsrfIs403() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        long w = workspaces();
        long m = members();
        mvc.perform(post("/workspaces").session(ben).contentType(MediaType.APPLICATION_JSON).content(named("Harbor")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("csrf_invalid"));
        assertThat(workspaces()).isEqualTo(w);
        assertThat(members()).isEqualTo(m);
    }

    // --- Separation between workspaces ---------------------------------------------------------------------------

    @Test
    @DisplayName("NFR-01, SP-07 · another account gets 404 on listWorkspaceMembers and createProject of the new workspace and does not see it in /auth/me")
    void otherAccountCannotReachNewWorkspace() throws Exception {
        String id = JsonPath.read(create(signIn("ben@example.com"), named("Private space"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.workspace_id");
        for (String email : new String[] {"ana@example.com", "carla@example.com", "vera@example.com"}) {
            MockHttpSession other = signIn(email);
            mvc.perform(get("/workspaces/" + id + "/members").session(other)).andExpect(status().isNotFound());
            long projects = count("SELECT count(*) FROM project");
            mvc.perform(post("/projects").session(other).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"workspace_id\":\"" + id + "\",\"name\":\"Intruder\"}"))
                    .andExpect(status().isNotFound());
            assertThat(count("SELECT count(*) FROM project")).isEqualTo(projects);
            mvc.perform(get("/auth/me").session(other))
                    .andExpect(jsonPath("$.memberships[?(@.workspace_id=='" + id + "')]", hasSize(0)));
        }
        assertThat(count("SELECT count(*) FROM member WHERE workspace_id = ?::uuid", id)).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-02, SP-07 · the creator sees only themselves in listWorkspaceMembers of the new workspace")
    void creatorIsTheOnlyMember() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        String body = create(ben, named("Solo space")).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        String id = JsonPath.read(body, "$.workspace_id");
        String memberId = JsonPath.read(body, "$.member_id");
        mvc.perform(get("/workspaces/" + id + "/members").session(ben))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].member_id").value(memberId));
    }

    @Test
    @DisplayName("FR-02, FR-06 · the creator, a Workspace Administrator, can create, edit and delete a project in the new workspace; owner, project member, listed, no rows left after delete")
    void creatorManagesProjectInNewWorkspace() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        String ws = create(ben, named("Admin projects")).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        String id = JsonPath.read(ws, "$.workspace_id");
        String memberId = JsonPath.read(ws, "$.member_id");

        String project = mvc.perform(post("/projects").session(ben).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workspace_id\":\"" + id + "\",\"name\":\"Admin project\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.owner_member_id").value(memberId))
                .andExpect(jsonPath("$.workspace_name").value("Admin projects"))
                .andReturn().getResponse().getContentAsString();
        String projectId = JsonPath.read(project, "$.project_id");

        entityManager.flush();
        assertThat(count("SELECT count(*) FROM project_member WHERE project_id = ?::uuid AND member_id = ?::uuid",
                projectId, memberId)).isEqualTo(1);
        mvc.perform(get("/projects").session(ben))
                .andExpect(jsonPath("$.items[?(@.project_id=='" + projectId + "')]", hasSize(1)));

        mvc.perform(patch("/projects/" + projectId).session(ben).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
        mvc.perform(delete("/projects/" + projectId).session(ben).with(csrf())).andExpect(status().is2xxSuccessful());
        entityManager.flush();
        assertThat(count("SELECT count(*) FROM project WHERE project_id = ?::uuid", projectId)).isZero();
        assertThat(count("SELECT count(*) FROM project_member WHERE project_id = ?::uuid", projectId)).isZero();
    }
}
