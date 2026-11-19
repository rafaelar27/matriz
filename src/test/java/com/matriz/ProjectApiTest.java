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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/**
 * Project operations through direct API requests (INC-03; FR-05, FR-06, FR-08, FR-18; US-06, US-24, US-27, US-28).
 * Project list for INC-06 screen 3a (AC-INC06.23, AC-INC06.24, AC-INC06.26, AC-INC06.28, AC-INC06.30; FR-08, NFR-01; US-19, US-28).
 * Project form for INC-06 screen 3b, an owner who was deactivated (AC-INC06.39, AC-INC06.46; FR-06; US-06, US-24; PD-10 in part).
 * Project owner required and date order for INC-07 (AC-INC07.1 to AC-INC07.7; FR-06, NFR-01; US-06, US-24).
 */
class ProjectApiTest extends ApiTestSupport {

    static final String ACME = "b0000000-0000-0000-0000-000000000001";
    static final String NORTHWIND = "b0000000-0000-0000-0000-000000000002";
    static final String ACME_CARLA = "c0000000-0000-0000-0000-000000000001";
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
        MvcResult created = create(ana, json)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Customer survey"))
                .andExpect(jsonPath("$.owner_member_id").value(ACME_ANA))
                .andExpect(jsonPath("$.owner_name").value("Ana Demo"))
                .andExpect(jsonPath("$.workspace_name").value("Acme Studio"))
                .andExpect(jsonPath("$.is_archived").value(false))
                .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.project_id");
        // R-02: createProject (openapi.yaml) answers with Location "/projects/{project_id}".
        assertThat(created.getResponse().getHeader("Location")).isEqualTo("/projects/" + id);

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
    @DisplayName("FR-05, FR-06 · Team Member and Viewer get 403 on create, edit and delete of projects")
    void teamMemberAndViewerCannotCreateEditAndDelete() throws Exception {
        long before = projectCount();
        for (String email : new String[] {"ben@example.com", "vera@example.com"}) {
            MockHttpSession s = signIn(email);
            create(s, named("Not allowed")).andExpect(status().isForbidden());
            update(s, WEBSITE, "{\"name\":\"Renamed\"}").andExpect(status().isForbidden());
            remove(s, WEBSITE).andExpect(status().isForbidden());
        }
        assertThat(projectCount()).isEqualTo(before);
        assertThat(websiteUnchanged()).isTrue();
    }

    @Test
    @DisplayName("FR-06, FR-05 · a Workspace Administrator creates a project: 201, owner defaults to her and she is a project member")
    void workspaceAdministratorCreatesProject() throws Exception {
        MockHttpSession carla = signIn("carla@example.com");
        String json = create(carla, named("Fictitious admin project")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.owner_member_id").value(ACME_CARLA))
                .andExpect(jsonPath("$.workspace_name").value("Acme Studio"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.project_id");
        entityManager.flush();
        assertThat(count("SELECT count(*) FROM project_member WHERE project_id = ?::uuid AND member_id = ?::uuid",
                id, ACME_CARLA)).isEqualTo(1);
        mvc.perform(get("/projects/" + id).session(carla)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("FR-06, FR-05 · a Workspace Administrator edits an existing project of the workspace")
    void workspaceAdministratorEditsProject() throws Exception {
        MockHttpSession carla = signIn("carla@example.com");
        String id = createdId(signIn("ana@example.com"), named("Fictitious pm project"));
        update(carla, id, "{\"name\":\"Fictitious renamed by admin\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Fictitious renamed by admin"));
        entityManager.flush();
        assertThat(count("SELECT count(*) FROM project WHERE project_id = ?::uuid AND name = 'Fictitious renamed by admin'",
                id)).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-06, FR-05, DA-05 · a Workspace Administrator deletes a project; no project, member or task row remains")
    void workspaceAdministratorDeletesProjectWithoutOrphans() throws Exception {
        MockHttpSession carla = signIn("carla@example.com");
        String id = createdId(carla, named("Fictitious admin delete"));
        String task = "e0000000-0000-0000-0000-0000000000f2";
        entityManager.flush();
        jdbc.update("INSERT INTO task (task_id, project_id, title, owner_member_id, status, created_by_member_id) "
                + "VALUES (?::uuid, ?::uuid, 'Fictitious task', ?::uuid, 'TO_DO', ?::uuid)", task, id, ACME_CARLA,
                ACME_CARLA);
        entityManager.clear();

        remove(carla, id).andExpect(status().is2xxSuccessful());
        entityManager.flush();
        assertThat(count("SELECT count(*) FROM project WHERE project_id = ?::uuid", id)).isZero();
        assertThat(count("SELECT count(*) FROM project_member WHERE project_id = ?::uuid", id)).isZero();
        assertThat(count("SELECT count(*) FROM task WHERE project_id = ?::uuid", id)).isZero();
        assertThat(count("SELECT count(*) FROM task WHERE task_id = ?::uuid", task)).isZero();
        mvc.perform(get("/projects/" + id).session(carla)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FR-05, PD-03 (prov.) · on a project she created, a Workspace Administrator still gets 403 on createTask, assign and comment")
    void workspaceAdministratorStillRefusedOnTasksOfOwnProject() throws Exception {
        MockHttpSession carla = signIn("carla@example.com");
        String id = createdId(carla, named("Fictitious admin tasks"));
        String task = "e0000000-0000-0000-0000-0000000000f1";
        entityManager.flush();
        jdbc.update("INSERT INTO task (task_id, project_id, title, owner_member_id, status, created_by_member_id) "
                + "VALUES (?::uuid, ?::uuid, 'Fictitious task', ?::uuid, 'TO_DO', ?::uuid)", task, id, ACME_CARLA,
                ACME_CARLA);
        entityManager.clear();
        long tasks = count("SELECT count(*) FROM task WHERE project_id = ?::uuid", id);

        mvc.perform(post("/projects/" + id + "/tasks").session(carla).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Fictitious new task\",\"owner_member_id\":\"" + ACME_CARLA + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/projects/" + id + "/tasks/" + task).session(carla).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Fictitious edit\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/projects/" + id + "/tasks/" + task).session(carla).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/projects/" + id + "/tasks/" + task + "/assignees").session(carla).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"member_ids\":[\"" + ACME_CARLA + "\"]}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/projects/" + id + "/tasks/" + task + "/assignees/" + ACME_CARLA).session(carla)
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/projects/" + id + "/tasks/" + task + "/comments").session(carla).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Fictitious comment\"}"))
                .andExpect(status().isForbidden());

        assertThat(count("SELECT count(*) FROM task WHERE project_id = ?::uuid", id)).isEqualTo(tasks);
        assertThat(count("SELECT count(*) FROM task WHERE task_id = ?::uuid AND title = 'Fictitious task'", task))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM task_assignee WHERE task_id = ?::uuid", task)).isZero();
        assertThat(count("SELECT count(*) FROM comment WHERE task_id = ?::uuid", task)).isZero();
    }

    @Test
    @DisplayName("NFR-01, FR-06 · a Workspace Administrator of another workspace gets 404 on createProject with Acme's workspace_id")
    void workspaceAdministratorOfAnotherWorkspaceGets404() throws Exception {
        long before = projectCount();
        create(signIn("finn@example.com"), named("Fictitious intruder")).andExpect(status().isNotFound());
        assertThat(projectCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("SP-04, FR-06 · a deactivated Workspace Administrator gets 403 on create, edit and delete and nothing changes")
    void deactivatedWorkspaceAdministratorIsRefused() throws Exception {
        MockHttpSession carla = signIn("carla@example.com");
        jdbc.update("UPDATE member SET status = 'DEACTIVATED' WHERE member_id = ?::uuid", ACME_CARLA);
        entityManager.clear();
        long before = projectCount();
        create(carla, named("Fictitious deactivated admin")).andExpect(status().isForbidden());
        update(carla, WEBSITE, "{\"name\":\"Renamed\"}").andExpect(status().isForbidden());
        remove(carla, WEBSITE).andExpect(status().isForbidden());
        assertThat(projectCount()).isEqualTo(before);
        assertThat(websiteUnchanged()).isTrue();
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
    @DisplayName("AC-INC07.2 · edit rules match create; a null owner is refused and null clears the description")
    void editRulesMatchCreate() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        update(ana, WEBSITE, "{\"name\":null}").andExpect(status().isUnprocessableEntity());
        update(ana, WEBSITE, "{\"name\":\"ab\"}").andExpect(status().isUnprocessableEntity());
        update(ana, WEBSITE, "{\"workspace_id\":\"" + NORTHWIND + "\"}").andExpect(status().isUnprocessableEntity());
        update(ana, WEBSITE, "{\"owner_member_id\":\"" + NW_DAN + "\"}").andExpect(status().isUnprocessableEntity());
        // A project always has an owner (decided 2026-11-12): null is refused, and the description is not cleared.
        update(ana, WEBSITE, "{\"description\":null,\"owner_member_id\":null}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("owner_member_id"));
        assertThat(count("SELECT count(*) FROM project WHERE project_id = ?::uuid AND owner_member_id = ?::uuid "
                + "AND description = 'New company homepage.'", WEBSITE, ACME_ANA)).isEqualTo(1);
        // A real request starts with an empty persistence context; drop what the refused request left in memory.
        entityManager.clear();
        mvc.perform(get("/projects/" + WEBSITE).session(ana))
                .andExpect(jsonPath("$.owner_member_id").value(ACME_ANA))
                .andExpect(jsonPath("$.description").value("New company homepage."));
        // null still clears an optional field.
        update(ana, WEBSITE, "{\"description\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.owner_member_id").value(ACME_ANA));
    }

    // --- INC-07: project owner required and date order (FR-06; decisions of 2026-11-12) ------------------------

    private static final String OWNER_REQUIRED = "An owner is required.";
    private static final String DATES_OUT_OF_ORDER = "The due date cannot be before the start date.";

    private static String inAcme(String fields) {
        return "{\"workspace_id\":\"" + ACME + "\"," + fields + "}";
    }

    private long projectCount() {
        return count("SELECT count(*) FROM project");
    }

    /** True when the stored Website redesign project still has its seed owner, name and dates. */
    private boolean websiteUnchanged() {
        return count("SELECT count(*) FROM project WHERE project_id = ?::uuid AND name = 'Website redesign' "
                + "AND owner_member_id = ?::uuid AND start_date = DATE '2026-11-01' AND due_date = DATE '2026-12-15' "
                + "AND is_archived = FALSE AND workspace_id = ?::uuid", WEBSITE, ACME_ANA, ACME) == 1;
    }

    /**
     * The tests share one persistence context across requests, while a real request starts with an empty one and a
     * refused request is rolled back. Drop what a refused request left in memory, without flushing it.
     */
    private void discardRefusedRequest() {
        entityManager.clear();
    }

    @Test
    @DisplayName("AC-INC07.1 · createProject with owner_member_id null is refused with 422 and saves nothing")
    void createWithNullOwnerIsRefused() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        long before = projectCount();

        create(ana, inAcme("\"name\":\"Fictitious null owner\",\"owner_member_id\":null"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.message").value("The project could not be saved."))
                .andExpect(jsonPath("$.details", hasSize(1)))
                .andExpect(jsonPath("$.details[0].field").value("owner_member_id"))
                .andExpect(jsonPath("$.details[0].issue").value(OWNER_REQUIRED));

        entityManager.flush();
        assertThat(projectCount()).isEqualTo(before);
        assertThat(count("SELECT count(*) FROM project WHERE name = 'Fictitious null owner'")).isZero();
        mvc.perform(get("/projects").param("q", "null owner").session(ana)).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("AC-INC07.1 · createProject without owner_member_id makes the caller the owner, also in the database")
    void createWithoutOwnerMakesTheCallerTheOwner() throws Exception {
        String id = createdId(signIn("ana@example.com"), named("Fictitious default owner"));
        entityManager.flush();
        assertThat(count("SELECT count(*) FROM project WHERE project_id = ?::uuid AND owner_member_id = ?::uuid",
                id, ACME_ANA)).isEqualTo(1);
        mvc.perform(get("/projects/" + id).session(signIn("vera@example.com")))
                .andExpect(jsonPath("$.owner_member_id").value(ACME_ANA))
                .andExpect(jsonPath("$.owner_name").value("Ana Demo"));
    }

    @Test
    @DisplayName("AC-INC07.2 · updateProject with owner_member_id null gives 422 and changes no field")
    void updateWithNullOwnerChangesNothing() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");

        update(ana, WEBSITE, "{\"name\":\"Fictitious rename\",\"owner_member_id\":null}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.message").value("The project could not be saved."))
                .andExpect(jsonPath("$.details[0].field").value("owner_member_id"))
                .andExpect(jsonPath("$.details[0].issue").value(OWNER_REQUIRED));

        assertThat(websiteUnchanged()).isTrue();
        discardRefusedRequest();
        mvc.perform(get("/projects/" + WEBSITE).session(signIn("vera@example.com")))
                .andExpect(jsonPath("$.name").value("Website redesign"))
                .andExpect(jsonPath("$.owner_member_id").value(ACME_ANA))
                .andExpect(jsonPath("$.owner_name").value("Ana Demo"));
    }

    @Test
    @DisplayName("AC-INC07.3 · a Viewer of the same workspace is accepted as owner, on create and on edit")
    void viewerCanBeChosenAsOwner() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        String id = createdId(ana, inAcme("\"name\":\"Fictitious viewer owned\",\"owner_member_id\":\"" + ACME_VERA
                + "\""));
        update(ana, TRADE_FAIR, "{\"owner_member_id\":\"" + ACME_VERA + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner_member_id").value(ACME_VERA))
                .andExpect(jsonPath("$.owner_name").value("Vera Demo"));
        entityManager.flush();

        assertThat(count("SELECT count(*) FROM project WHERE project_id IN (?::uuid, ?::uuid) "
                + "AND owner_member_id = ?::uuid", id, TRADE_FAIR, ACME_VERA)).isEqualTo(2);
        MockHttpSession vera = signIn("vera@example.com");
        mvc.perform(get("/projects/" + id).session(vera)).andExpect(jsonPath("$.owner_member_id").value(ACME_VERA));
        mvc.perform(get("/projects/" + TRADE_FAIR).session(vera))
                .andExpect(jsonPath("$.owner_member_id").value(ACME_VERA));
    }

    @Test
    @DisplayName("AC-INC07.3 · an owner who is not a valid id, unknown, or the caller's own member in another workspace is refused")
    void ownerThatIsNotAnActiveMemberOfTheWorkspaceIsRefused() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        long before = projectCount();
        // NW_ANA is Ana's own membership in Northwind Lab; "c0000000-…-999" belongs to nobody.
        String nwAna = "c0000000-0000-0000-0000-000000000006";
        assertThat(count("SELECT count(*) FROM member WHERE member_id = ?::uuid AND workspace_id = ?::uuid",
                nwAna, NORTHWIND)).isEqualTo(1);
        for (String owner : new String[] {"not-an-id", "", "c0000000-0000-0000-0000-000000000999", nwAna}) {
            create(ana, inAcme("\"name\":\"Fictitious bad owner\",\"owner_member_id\":\"" + owner + "\""))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.details[0].field").value("owner_member_id"));
            update(ana, WEBSITE, "{\"owner_member_id\":\"" + owner + "\"}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.details[0].field").value("owner_member_id"));
        }
        assertThat(projectCount()).isEqualTo(before);
        assertThat(websiteUnchanged()).isTrue();
    }

    @Test
    @DisplayName("AC-INC07.3 · a deactivated member of the workspace is refused as owner, on create and on edit")
    void deactivatedMemberIsRefusedAsOwner() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        long before = projectCount();
        // Only inside this test's rolled-back transaction.
        jdbc.update("UPDATE member SET status = 'DEACTIVATED' WHERE member_id = ?::uuid", ACME_BEN);
        entityManager.clear();

        create(ana, inAcme("\"name\":\"Fictitious deactivated owner\",\"owner_member_id\":\"" + ACME_BEN + "\""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("owner_member_id"));
        update(ana, WEBSITE, "{\"owner_member_id\":\"" + ACME_BEN + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("owner_member_id"));

        assertThat(projectCount()).isEqualTo(before);
        assertThat(websiteUnchanged()).isTrue();
    }

    @Test
    @DisplayName("AC-INC07.4 · createProject accepts start = due, only one date, or neither, and the database keeps them")
    void createAcceptsDatesInOrderOrMissing() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        String same = createdId(ana, inAcme("\"name\":\"Fictitious same day\","
                + "\"start_date\":\"2026-12-01\",\"due_date\":\"2026-12-01\""));
        String onlyStart = createdId(ana, inAcme("\"name\":\"Fictitious only start\",\"start_date\":\"2027-05-01\""));
        String onlyDue = createdId(ana, inAcme("\"name\":\"Fictitious only due\",\"due_date\":\"2025-01-01\""));
        String neither = createdId(ana, named("Fictitious no dates"));
        String nulls = createdId(ana, inAcme("\"name\":\"Fictitious null dates\",\"start_date\":null,"
                + "\"due_date\":null"));
        // Flushing runs the database CHECK (migration V4) on the new rows.
        entityManager.flush();

        assertThat(count("SELECT count(*) FROM project WHERE project_id = ?::uuid AND start_date = DATE '2026-12-01' "
                + "AND due_date = DATE '2026-12-01'", same)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM project WHERE project_id = ?::uuid AND start_date = DATE '2027-05-01' "
                + "AND due_date IS NULL", onlyStart)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM project WHERE project_id = ?::uuid AND start_date IS NULL "
                + "AND due_date = DATE '2025-01-01'", onlyDue)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM project WHERE project_id IN (?::uuid, ?::uuid) AND start_date IS NULL "
                + "AND due_date IS NULL", neither, nulls)).isEqualTo(2);
        mvc.perform(get("/projects/" + same).session(signIn("vera@example.com")))
                .andExpect(jsonPath("$.start_date").value("2026-12-01"))
                .andExpect(jsonPath("$.due_date").value("2026-12-01"));
    }

    @Test
    @DisplayName("AC-INC07.4 · createProject with the start date one day after the due date gives 422 on due_date")
    void createWithStartAfterDueIsRefused() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        long before = projectCount();

        create(ana, inAcme("\"name\":\"Fictitious late start\",\"start_date\":\"2026-12-02\","
                + "\"due_date\":\"2026-12-01\""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.message").value("The project could not be saved."))
                .andExpect(jsonPath("$.details", hasSize(1)))
                .andExpect(jsonPath("$.details[0].field").value("due_date"))
                .andExpect(jsonPath("$.details[0].issue").value(DATES_OUT_OF_ORDER));

        entityManager.flush();
        assertThat(projectCount()).isEqualTo(before);
        assertThat(count("SELECT count(*) FROM project WHERE name = 'Fictitious late start'")).isZero();
    }

    @Test
    @DisplayName("AC-INC07.5 · on edit, a single date that puts the start after the stored due date is refused and changes nothing")
    void editRefusesASingleDateThatBreaksTheOrder() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        MockHttpSession vera = signIn("vera@example.com");
        // Website redesign is stored with start 2026-11-01 and due 2026-12-15.
        assertThat(websiteUnchanged()).isTrue();

        update(ana, WEBSITE, "{\"due_date\":\"2026-10-31\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("due_date"))
                .andExpect(jsonPath("$.details[0].issue").value(DATES_OUT_OF_ORDER));
        assertThat(websiteUnchanged()).isTrue();
        discardRefusedRequest();

        update(ana, WEBSITE, "{\"start_date\":\"2026-12-16\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("due_date"))
                .andExpect(jsonPath("$.details[0].issue").value(DATES_OUT_OF_ORDER));
        assertThat(websiteUnchanged()).isTrue();
        discardRefusedRequest();

        // A refused request changes none of the other fields sent with it.
        update(ana, WEBSITE, "{\"name\":\"Fictitious rename\",\"description\":null,\"due_date\":\"2026-10-31\"}")
                .andExpect(status().isUnprocessableEntity());
        assertThat(websiteUnchanged()).isTrue();
        assertThat(count("SELECT count(*) FROM project WHERE project_id = ?::uuid "
                + "AND description = 'New company homepage.'", WEBSITE)).isEqualTo(1);
        discardRefusedRequest();

        mvc.perform(get("/projects/" + WEBSITE).session(vera))
                .andExpect(jsonPath("$.name").value("Website redesign"))
                .andExpect(jsonPath("$.description").value("New company homepage."))
                .andExpect(jsonPath("$.start_date").value("2026-11-01"))
                .andExpect(jsonPath("$.due_date").value("2026-12-15"));
    }

    @Test
    @DisplayName("AC-INC07.5 · on edit, the rule applies to the resulting project: equal dates, both dates moved together, a cleared date")
    void editChecksTheResultingProject() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");

        // Due date moved to the stored start date: start = due is accepted.
        update(ana, WEBSITE, "{\"due_date\":\"2026-11-01\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.start_date").value("2026-11-01"))
                .andExpect(jsonPath("$.due_date").value("2026-11-01"));
        // Both dates moved past the stored due date in one request: the result is in order.
        update(ana, WEBSITE, "{\"start_date\":\"2027-01-10\",\"due_date\":\"2027-01-20\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.start_date").value("2027-01-10"))
                .andExpect(jsonPath("$.due_date").value("2027-01-20"));
        // Clearing the start date is accepted; afterwards any due date is.
        update(ana, WEBSITE, "{\"start_date\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.start_date").doesNotExist())
                .andExpect(jsonPath("$.due_date").value("2027-01-20"));
        update(ana, WEBSITE, "{\"due_date\":\"2026-01-01\"}").andExpect(status().isOk());
        // Clearing the due date on another project, then a start date after its old due date, is accepted.
        update(ana, TRADE_FAIR, "{\"due_date\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.due_date").doesNotExist())
                .andExpect(jsonPath("$.start_date").value("2026-11-10"));
        update(ana, TRADE_FAIR, "{\"start_date\":\"2027-06-01\"}").andExpect(status().isOk());
        // Flushing runs the database CHECK (migration V4) on the changed rows.
        entityManager.flush();
        entityManager.clear();

        assertThat(count("SELECT count(*) FROM project WHERE project_id = ?::uuid AND start_date IS NULL "
                + "AND due_date = DATE '2026-01-01'", WEBSITE)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM project WHERE project_id = ?::uuid AND start_date = DATE '2027-06-01' "
                + "AND due_date IS NULL", TRADE_FAIR)).isEqualTo(1);
        mvc.perform(get("/projects/" + TRADE_FAIR).session(signIn("vera@example.com")))
                .andExpect(jsonPath("$.start_date").value("2027-06-01"))
                .andExpect(jsonPath("$.due_date").doesNotExist());
    }

    @Test
    @DisplayName("AC-INC07.6 · a Team Member or Viewer sending a null owner or dates out of order gets 403; a Workspace Administrator gets 422")
    void accessIsCheckedBeforeValidationInsideTheWorkspace() throws Exception {
        long before = projectCount();
        for (String email : new String[] {"ben@example.com", "vera@example.com", "carla@example.com"}) {
            MockHttpSession s = signIn(email);
            // The Workspace Administrator passes the access check, so validation applies: 422.
            int expected = email.startsWith("carla") ? 422 : 403;
            create(s, inAcme("\"name\":\"Fictitious refused\",\"owner_member_id\":null"))
                    .andExpect(status().is(expected));
            create(s, inAcme("\"name\":\"Fictitious refused\",\"start_date\":\"2026-12-02\","
                    + "\"due_date\":\"2026-12-01\"")).andExpect(status().is(expected));
            update(s, WEBSITE, "{\"owner_member_id\":null}").andExpect(status().is(expected));
            update(s, WEBSITE, "{\"due_date\":\"2026-10-31\"}").andExpect(status().is(expected));
        }
        assertThat(projectCount()).isEqualTo(before);
        assertThat(websiteUnchanged()).isTrue();
    }

    @Test
    @DisplayName("AC-INC07.6 · NFR-01 · a member of another workspace sending a null owner or dates out of order gets 404")
    void accessIsCheckedBeforeValidationFromAnotherWorkspace() throws Exception {
        MockHttpSession dan = signIn("dan@example.com");
        long before = projectCount();

        create(dan, inAcme("\"name\":\"Fictitious refused\",\"owner_member_id\":null")).andExpect(status().isNotFound());
        create(dan, inAcme("\"name\":\"Fictitious refused\",\"start_date\":\"2026-12-02\",\"due_date\":\"2026-12-01\""))
                .andExpect(status().isNotFound());
        update(dan, WEBSITE, "{\"owner_member_id\":null}").andExpect(status().isNotFound());
        update(dan, WEBSITE, "{\"due_date\":\"2026-10-31\"}").andExpect(status().isNotFound());

        assertThat(projectCount()).isEqualTo(before);
        assertThat(websiteUnchanged()).isTrue();
    }

    @Test
    @DisplayName("AC-INC07.7 · updateProject refuses created_by_member_id, is_archived and workspace_id with 422")
    void updateRefusesIdentityAndServerSetFields() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        String[][] cases = {
                {"created_by_member_id", "\"" + ACME_BEN + "\""},
                {"is_archived", "true"},
                {"workspace_id", "\"" + NORTHWIND + "\""}};
        for (String[] c : cases) {
            update(ana, WEBSITE, "{\"name\":\"Fictitious takeover\",\"" + c[0] + "\":" + c[1] + "}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("validation_failed"))
                    .andExpect(jsonPath("$.details[0].field").value(c[0]));
            assertThat(websiteUnchanged()).isTrue();
            discardRefusedRequest();
        }
        mvc.perform(get("/projects/" + WEBSITE).session(ana))
                .andExpect(jsonPath("$.name").value("Website redesign"))
                .andExpect(jsonPath("$.workspace_id").value(ACME))
                .andExpect(jsonPath("$.is_archived").value(false));
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

    @Test
    @DisplayName("S-01 · FR-08, listProjects · page*size above 2147483647 is refused with 422 on field page, never 500")
    void hugePageIsRefusedWithValidationFailed() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        mvc.perform(get("/projects").param("page", "2147483647").param("size", "100").session(ana))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.details[0].field").value("page"));
    }

    @Test
    @DisplayName("S-01 · FR-08, listProjects · guard: a large but valid page is 200 with no items, page 0 still works")
    void largeValidPageStillAnswersWithEmptyItems() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        mvc.perform(get("/projects").param("page", "21474836").param("size", "100").session(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.total").value(3));
        mvc.perform(get("/projects").param("page", "0").param("size", "20").session(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)));
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

    // --- Project form for INC-06 screen 3b: an owner who was deactivated (decision of 2026-11-12, PD-10 in part) --

    /**
     * Ana creates a fictitious Acme project owned by Ben, then Ben's Acme membership is deactivated, only inside this
     * test's rolled-back transaction. Returns the project id.
     */
    private String projectOwnedByADeactivatedBen(MockHttpSession ana, String name) throws Exception {
        String id = createdId(ana, inAcme("\"name\":\"" + name + "\",\"owner_member_id\":\"" + ACME_BEN + "\""));
        entityManager.flush();
        jdbc.update("UPDATE member SET status = 'DEACTIVATED' WHERE member_id = ?::uuid", ACME_BEN);
        entityManager.clear();
        assertThat(count("SELECT count(*) FROM project WHERE project_id = ?::uuid AND owner_member_id = ?::uuid",
                id, ACME_BEN)).isEqualTo(1);
        return id;
    }

    @Test
    @DisplayName("AC-INC06.46 · listWorkspaceMembers leaves out a deactivated member")
    void workspaceMembersLeaveOutADeactivatedMember() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        String before = mvc.perform(get("/workspaces/" + ACME + "/members").session(ana))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(before, "$[*].member_id")).contains(ACME_BEN);

        projectOwnedByADeactivatedBen(ana, "Fictitious former owner list");

        String after = mvc.perform(get("/workspaces/" + ACME + "/members").session(ana))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(after, "$[*].member_id");
        assertThat(ids).doesNotContain(ACME_BEN);
        assertThat(ids).hasSize(JsonPath.<List<String>>read(before, "$[*].member_id").size() - 1);
        assertThat(ids).contains(ACME_ANA, ACME_VERA);
    }

    @Test
    @DisplayName("AC-INC06.46 · a PATCH without owner_member_id on a project whose owner is deactivated keeps that owner")
    void patchWithoutOwnerKeepsADeactivatedOwner() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        String id = projectOwnedByADeactivatedBen(ana, "Fictitious former owner keep");

        update(ana, id, "{\"name\":\"Fictitious former owner renamed\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Fictitious former owner renamed"))
                .andExpect(jsonPath("$.owner_member_id").value(ACME_BEN));
        entityManager.flush();
        entityManager.clear();

        assertThat(count("SELECT count(*) FROM project WHERE project_id = ?::uuid AND owner_member_id = ?::uuid "
                + "AND name = 'Fictitious former owner renamed'", id, ACME_BEN)).isEqualTo(1);
        mvc.perform(get("/projects/" + id).session(signIn("vera@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Fictitious former owner renamed"))
                .andExpect(jsonPath("$.owner_member_id").value(ACME_BEN));
    }

    @Test
    @DisplayName("AC-INC06.46 · re-sending a deactivated owner's id gives 422 on owner_member_id and changes nothing")
    void resendingADeactivatedOwnerIsRefused() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        String id = projectOwnedByADeactivatedBen(ana, "Fictitious former owner resend");

        update(ana, id, "{\"name\":\"Fictitious former owner changed\",\"owner_member_id\":\"" + ACME_BEN + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.details[0].field").value("owner_member_id"));

        assertThat(count("SELECT count(*) FROM project WHERE project_id = ?::uuid AND owner_member_id = ?::uuid "
                + "AND name = 'Fictitious former owner resend'", id, ACME_BEN)).isEqualTo(1);
        discardRefusedRequest();
        mvc.perform(get("/projects/" + id).session(signIn("vera@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Fictitious former owner resend"))
                .andExpect(jsonPath("$.owner_member_id").value(ACME_BEN));
    }

    @Test
    @DisplayName("AC-INC06.39 · QG4 · the project detail and list still show a deactivated owner's name")
    void detailAndListStillShowADeactivatedOwnersName() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        String id = projectOwnedByADeactivatedBen(ana, "Fictitious former owner name");
        MockHttpSession vera = signIn("vera@example.com");

        mvc.perform(get("/projects/" + id).session(vera))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner_member_id").value(ACME_BEN))
                .andExpect(jsonPath("$.owner_name").value("Ben Demo"));
        mvc.perform(get("/projects").param("q", "former owner name").session(vera))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].project_id").value(id))
                .andExpect(jsonPath("$.items[0].owner_member_id").value(ACME_BEN))
                .andExpect(jsonPath("$.items[0].owner_name").value("Ben Demo"));
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
