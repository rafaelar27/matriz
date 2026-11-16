package com.matriz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/**
 * Project operations through direct API requests (INC-03; FR-05, FR-06, FR-08, FR-18; US-06, US-24, US-27, US-28).
 * Project list for INC-06 screen 3a (AC-INC06.23, AC-INC06.24, AC-INC06.26, AC-INC06.28, AC-INC06.30; FR-08, NFR-01; US-19, US-28).
 */
class ProjectApiTest extends ApiTestSupport {

    static final String ACME = "b0000000-0000-0000-0000-000000000001";
    static final String NORTHWIND = "b0000000-0000-0000-0000-000000000002";
    static final String NW_DAN = "c0000000-0000-0000-0000-000000000007";

    @PersistenceContext
    private EntityManager entityManager;

    private ResultActions create(MockHttpSession s, String json) throws Exception {
        return mvc.perform(post("/projects").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions update(MockHttpSession s, String id, String json) throws Exception {
        return mvc.perform(patch("/projects/" + id).session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions remove(MockHttpSession s, String id) throws Exception {
        return mvc.perform(delete("/projects/" + id).session(s).with(csrf()));
    }

    private static String named(String name) {
        return "{\"workspace_id\":\"" + ACME + "\",\"name\":\"" + name + "\"}";
    }

    private String createdId(MockHttpSession s, String json) throws Exception {
        String body = create(s, json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.project_id");
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    // --- Create, view, list (increment scenario at API level) ----------------------------------------------------

    @Test
    void projectManagerCreatesAProjectAndSeesItInTheListAndDetail() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        String json = "{\"workspace_id\":\"" + ACME + "\",\"name\":\"  Customer survey  \","
                + "\"description\":\"Short survey.\",\"start_date\":\"2026-11-16\",\"due_date\":\"2026-12-18\"}";
        String body = create(ana, json)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Customer survey"))
                .andExpect(jsonPath("$.owner_member_id").value(ACME_ANA))
                .andExpect(jsonPath("$.owner_name").value("Ana Demo"))
                .andExpect(jsonPath("$.workspace_name").value("Acme Studio"))
                .andExpect(jsonPath("$.is_archived").value(false))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.project_id");

        mvc.perform(get("/projects/" + id).session(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Short survey."))
                .andExpect(jsonPath("$.due_date").value("2026-12-18"));
        mvc.perform(get("/projects").param("q", "customer").session(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].project_id").value(id))
                .andExpect(jsonPath("$.items[0].description").doesNotExist());
        // The creator becomes a project member (decided 2026-11-12).
        assertThat(count("SELECT count(*) FROM project_member WHERE project_id = ?::uuid AND member_id = ?::uuid",
                id, ACME_ANA)).isEqualTo(1);
    }

    @Test
    void anotherMemberCanBeChosenAsOwner() throws Exception {
        create(signIn("ana@example.com"), "{\"workspace_id\":\"" + ACME + "\",\"name\":\"Office move\","
                + "\"owner_member_id\":\"" + ACME_BEN + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.owner_member_id").value(ACME_BEN))
                .andExpect(jsonPath("$.owner_name").value("Ben Demo"));
    }

    // --- Validation boundaries ---------------------------------------------------------------------------------

    @Test
    void nameLengthBoundaries() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        create(ana, named("ab")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("name"));
        create(ana, named("abc")).andExpect(status().isCreated());
        create(ana, named("n".repeat(100))).andExpect(status().isCreated());
        create(ana, named("n".repeat(101))).andExpect(status().isUnprocessableEntity());
        create(ana, named("  ab  ")).andExpect(status().isUnprocessableEntity());
        create(ana, "{\"workspace_id\":\"" + ACME + "\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("name"));
    }

    @Test
    void descriptionLengthBoundaries() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        create(ana, "{\"workspace_id\":\"" + ACME + "\",\"name\":\"Long text\",\"description\":\"" + "d".repeat(1000)
                + "\"}").andExpect(status().isCreated());
        create(ana, "{\"workspace_id\":\"" + ACME + "\",\"name\":\"Long text\",\"description\":\"" + "d".repeat(1001)
                + "\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("description"));
    }

    @Test
    void invalidDatesAndUnknownFieldsAreRefused() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        create(ana, "{\"workspace_id\":\"" + ACME + "\",\"name\":\"Dates\",\"due_date\":\"2026-02-30\"}")
                .andExpect(status().isUnprocessableEntity());
        create(ana, "{\"workspace_id\":\"" + ACME + "\",\"name\":\"Flag\",\"is_archived\":true}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("is_archived"));
    }

    // --- Ownership comes from authentication -------------------------------------------------------------------

    @Test
    void ownerFromAnotherWorkspaceIsRefused() throws Exception {
        create(signIn("ana@example.com"), "{\"workspace_id\":\"" + ACME + "\",\"name\":\"Spoof\",\"owner_member_id\":\""
                + NW_DAN + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("owner_member_id"));
    }

    @Test
    void creatingInAWorkspaceTheCallerIsNotInIsNotFound() throws Exception {
        create(signIn("dan@example.com"), named("Not mine")).andExpect(status().isNotFound());
    }

    // --- Roles ---------------------------------------------------------------------------------------------------

    @Test
    void onlyProjectManagersCreateEditAndDelete() throws Exception {
        for (String email : new String[] {"ben@example.com", "vera@example.com", "carla@example.com"}) {
            MockHttpSession s = signIn(email);
            create(s, named("Not allowed")).andExpect(status().isForbidden());
            update(s, WEBSITE, "{\"name\":\"Renamed\"}").andExpect(status().isForbidden());
            remove(s, WEBSITE).andExpect(status().isForbidden());
        }
    }

    @Test
    void everyMemberOfTheWorkspaceCanViewIncludingViewers() throws Exception {
        mvc.perform(get("/projects/" + WEBSITE).session(signIn("vera@example.com"))).andExpect(status().isOk());
        mvc.perform(get("/projects/" + TRADE_FAIR).session(signIn("carla@example.com"))).andExpect(status().isOk());
    }

    // --- Access from another workspace (NFR-01) ----------------------------------------------------------------

    @Test
    void userOfAnotherWorkspaceCannotListViewEditOrDelete() throws Exception {
        String id = createdId(signIn("ana@example.com"), named("Acme only"));
        MockHttpSession dan = signIn("dan@example.com");

        mvc.perform(get("/projects").session(dan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Lab inventory"));
        mvc.perform(get("/projects").param("q", "acme").session(dan)).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/projects/" + id).session(dan)).andExpect(status().isNotFound());
        update(dan, id, "{\"name\":\"Taken over\"}").andExpect(status().isNotFound());
        remove(dan, id).andExpect(status().isNotFound());
        mvc.perform(get("/progress").param("project_id", id).session(dan))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/workspaces/" + ACME + "/members").session(dan)).andExpect(status().isNotFound());
        assertThat(count("SELECT count(*) FROM project WHERE project_id = ?::uuid AND name = 'Acme only'", id))
                .isEqualTo(1);
    }

    // --- Edit --------------------------------------------------------------------------------------------------

    @Test
    void projectManagerEditsTheDueDate() throws Exception {
        // AC-24.1
        MockHttpSession ana = signIn("ana@example.com");
        update(ana, WEBSITE, "{\"due_date\":\"2026-12-22\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.due_date").value("2026-12-22"))
                .andExpect(jsonPath("$.name").value("Website redesign"));
        mvc.perform(get("/projects").param("q", "website").session(ana))
                .andExpect(jsonPath("$.items[0].due_date").value("2026-12-22"));
    }

    @Test
    void editRulesMatchCreate() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        update(ana, WEBSITE, "{\"name\":null}").andExpect(status().isUnprocessableEntity());
        update(ana, WEBSITE, "{\"name\":\"ab\"}").andExpect(status().isUnprocessableEntity());
        update(ana, WEBSITE, "{\"workspace_id\":\"" + NORTHWIND + "\"}").andExpect(status().isUnprocessableEntity());
        update(ana, WEBSITE, "{\"owner_member_id\":\"" + NW_DAN + "\"}").andExpect(status().isUnprocessableEntity());
        update(ana, WEBSITE, "{\"description\":null,\"owner_member_id\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.owner_member_id").doesNotExist());
    }

    // --- Delete: dependent records are removed (no orphans) ------------------------------------------------------

    @Test
    void deletingAProjectRemovesItsTasksCommentsNotificationsAndMembers() throws Exception {
        jdbc.update("INSERT INTO notification (recipient_member_id, type, task_id) VALUES (?::uuid, 'TASK_ASSIGNED', ?::uuid)",
                ACME_BEN, T_IMAGES);
        assertThat(count("SELECT count(*) FROM task WHERE project_id = ?::uuid", WEBSITE)).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM comment c JOIN task t USING (task_id) WHERE t.project_id = ?::uuid",
                WEBSITE)).isEqualTo(2);

        remove(signIn("ana@example.com"), WEBSITE).andExpect(status().isNoContent());
        entityManager.clear();

        assertThat(count("SELECT count(*) FROM project WHERE project_id = ?::uuid", WEBSITE)).isZero();
        assertThat(count("SELECT count(*) FROM task WHERE project_id = ?::uuid", WEBSITE)).isZero();
        assertThat(count("SELECT count(*) FROM project_member WHERE project_id = ?::uuid", WEBSITE)).isZero();
        // No orphans anywhere in the database.
        assertThat(count("SELECT count(*) FROM task t LEFT JOIN project p USING (project_id) WHERE p.project_id IS NULL"))
                .isZero();
        assertThat(count("SELECT count(*) FROM comment c LEFT JOIN task t USING (task_id) WHERE t.task_id IS NULL"))
                .isZero();
        assertThat(count("SELECT count(*) FROM notification n LEFT JOIN task t USING (task_id) WHERE t.task_id IS NULL"))
                .isZero();
        // Other projects are untouched.
        assertThat(count("SELECT count(*) FROM task WHERE project_id = ?::uuid", TRADE_FAIR)).isEqualTo(1);
        mvc.perform(get("/projects/" + WEBSITE).session(signIn("ana@example.com"))).andExpect(status().isNotFound());
    }

    // --- Search and pagination ---------------------------------------------------------------------------------

    @Test
    void searchMatchesPartOfTheNameIgnoringCaseAndOnlyAccessibleProjects() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        mvc.perform(get("/projects").param("q", "  TRADE ").session(ben))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Trade fair stand"));
        // "Lab inventory" is in Northwind Lab, where Ben is not a member.
        mvc.perform(get("/projects").param("q", "lab").session(ben)).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/projects").param("q", "%").session(ben)).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/projects").session(ben))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].name").value("Trade fair stand"))
                .andExpect(jsonPath("$.items[1].name").value("Website redesign"));
    }

    @Test
    void paginationFollowsTheContract() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        mvc.perform(get("/projects").param("size", "2").session(ana))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].name").value("Lab inventory"));
        mvc.perform(get("/projects").param("size", "2").param("page", "1").session(ana))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("Website redesign"));
        mvc.perform(get("/projects").param("workspace_id", ACME).session(ana)).andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/projects").session(ana)).andExpect(jsonPath("$.size").value(20));
        for (String[] bad : new String[][] {{"size", "0"}, {"size", "101"}, {"page", "-1"}, {"page", "x"}}) {
            mvc.perform(get("/projects").param(bad[0], bad[1]).session(ana))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    // --- Project list for INC-06 screen 3a -----------------------------------------------------------------------

    private String listBody(MockHttpSession s, String... params) throws Exception {
        var request = get("/projects").session(s);
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("AC-INC06.23 · a Viewer lists both Acme projects, the same items as a Project Manager")
    void viewerListsTheSameAcmeProjectsAsAProjectManager() throws Exception {
        String vera = listBody(signIn("vera@example.com"));
        String ana = listBody(signIn("ana@example.com"), "workspace_id", ACME);

        assertThat((Integer) JsonPath.read(vera, "$.total")).isEqualTo(2);
        List<String> veraIds = JsonPath.read(vera, "$.items[*].project_id");
        List<String> anaIds = JsonPath.read(ana, "$.items[*].project_id");
        assertThat(veraIds).containsExactly(TRADE_FAIR, WEBSITE);
        assertThat(veraIds).isEqualTo(anaIds);
        assertThat((Object) JsonPath.read(vera, "$.items")).isEqualTo(JsonPath.read(ana, "$.items"));
    }

    @Test
    @DisplayName("AC-INC06.24 · FR-08 · a Team Member lists an Acme project they are not a member of")
    void teamMemberListsAProjectTheyAreNotAMemberOf() throws Exception {
        String id = createdId(signIn("ana@example.com"), named("Fictitious supplier review"));
        assertThat(count("SELECT count(*) FROM project_member WHERE project_id = ?::uuid AND member_id = ?::uuid",
                id, ACME_BEN)).isZero();

        String ben = listBody(signIn("ben@example.com"), "workspace_id", ACME);

        List<String> benIds = JsonPath.read(ben, "$.items[*].project_id");
        assertThat(benIds).contains(id);
        assertThat((Integer) JsonPath.read(ben, "$.total")).isEqualTo(3);
    }

    @Test
    @DisplayName("AC-INC06.26 · q of 100 characters is accepted, 101 characters is refused with 422")
    void searchTextLengthBoundaries() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        mvc.perform(get("/projects").param("q", "q".repeat(100)).session(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/projects").param("q", "q".repeat(101)).session(ana))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("q"));
    }

    @Test
    @DisplayName("AC-INC06.26 · q of whitespace only lists every project, the same as no q")
    void whitespaceOnlySearchListsEverything() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        String all = listBody(ana);
        String blank = listBody(ana, "q", "   ");
        assertThat((Integer) JsonPath.read(blank, "$.total")).isEqualTo(3);
        assertThat((Object) JsonPath.read(blank, "$.items")).isEqualTo(JsonPath.read(all, "$.items"));
        assertThat((Integer) JsonPath.read(blank, "$.total")).isEqualTo(JsonPath.read(all, "$.total"));
    }

    @Test
    @DisplayName("AC-INC06.26 · q \"WEB\" matches \"Website redesign\" only")
    void upperCaseSearchMatchesWebsiteOnly() throws Exception {
        mvc.perform(get("/projects").param("q", "WEB").session(signIn("ana@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].project_id").value(WEBSITE))
                .andExpect(jsonPath("$.items[0].name").value("Website redesign"));
    }

    @Test
    @DisplayName("AC-INC06.28 · a page past the last returns 200, no items and the real total")
    void pagePastTheLastIsEmptyWithTheRealTotal() throws Exception {
        mvc.perform(get("/projects").param("page", "99").param("size", "20").session(signIn("ana@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(99))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    @DisplayName("AC-INC06.30 · NFR-01 · a member filtering by another workspace's id gets total 0 and no items")
    void filteringByAWorkspaceTheCallerIsNotInIsEmpty() throws Exception {
        mvc.perform(get("/projects").param("workspace_id", NORTHWIND).session(signIn("ben@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    // --- Progress and members (supporting the UI) ---------------------------------------------------------------

    @Test
    void progressCountsDoneTasks() throws Exception {
        String empty = createdId(signIn("ana@example.com"), named("Empty one"));
        mvc.perform(get("/progress").param("project_id", WEBSITE, empty).session(signIn("vera@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].done").value(1))
                .andExpect(jsonPath("$[0].total").value(3))
                .andExpect(jsonPath("$[1].done").value(0))
                .andExpect(jsonPath("$[1].total").value(0));
    }

    @Test
    void workspaceMembersAreListedForOwnerChoice() throws Exception {
        mvc.perform(get("/workspaces/" + ACME + "/members").session(signIn("ana@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].name").value("Ana Demo"))
                .andExpect(jsonPath("$[0].role").value("Project Manager"));
    }

    // --- Authentication ----------------------------------------------------------------------------------------

    @Test
    void everyProjectOperationNeedsASession() throws Exception {
        mvc.perform(get("/projects")).andExpect(status().isUnauthorized());
        mvc.perform(get("/projects/" + WEBSITE)).andExpect(status().isUnauthorized());
        mvc.perform(post("/projects").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(named("x y z")))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/projects/" + WEBSITE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/projects/" + WEBSITE).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(get("/progress").param("project_id", WEBSITE)).andExpect(status().isUnauthorized());
        mvc.perform(get("/workspaces/" + ACME + "/members")).andExpect(status().isUnauthorized());
    }
}
