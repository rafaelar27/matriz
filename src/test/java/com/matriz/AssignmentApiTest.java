package com.matriz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Task assignees through direct API requests (INC-05 Feature A; openapi.yaml 0.5.0-draft; FR-22, US-29, BR-22).
 * Demo data: "Website redesign" has project members Ana (Project Manager), Ben (Team Member) and Vera (Viewer);
 * Carla is a Workspace Administrator of Acme Studio but not a member of the project.
 */
class AssignmentApiTest extends ApiTestSupport {

    static final String ACME_CARLA = "c0000000-0000-0000-0000-000000000001";
    static final String T_KICKOFF = "e0000000-0000-0000-0000-000000000004";

    @PersistenceContext
    private EntityManager entityManager;

    private static String base(String project, String task) {
        return "/projects/" + project + "/tasks/" + task;
    }

    private ResultActions assign(MockHttpSession s, String project, String task, String... ids) throws Exception {
        String list = java.util.Arrays.stream(ids).map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
        return assignRaw(s, project, task, "{\"member_ids\":[" + list + "]}");
    }

    private ResultActions assignRaw(MockHttpSession s, String project, String task, String json) throws Exception {
        return mvc.perform(post(base(project, task) + "/assignees").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions unassign(MockHttpSession s, String project, String task, String member) throws Exception {
        return mvc.perform(delete(base(project, task) + "/assignees/" + member).session(s).with(csrf()));
    }

    private ResultActions assignees(MockHttpSession s, String project, String task) throws Exception {
        return mvc.perform(get(base(project, task) + "/assignees").session(s));
    }

    private long rows(String task) {
        return jdbc.queryForObject("SELECT count(*) FROM task_assignee WHERE task_id = ?::uuid", Long.class, task);
    }

    // --- Eligible members -----------------------------------------------------------------------------------------

    @Test
    void onlyActiveProjectMembersAreEligibleAndNoAccountDataIsShown() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        mvc.perform(get(base(WEBSITE, T_LAYOUT) + "/eligible-assignees").session(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].member_id", contains(ACME_ANA, ACME_BEN, ACME_VERA)))
                .andExpect(jsonPath("$[*].name", contains("Ana Demo", "Ben Demo", "Vera Demo")))
                .andExpect(jsonPath("$[2].role").value("Viewer"))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].account_id").doesNotExist())
                .andExpect(jsonPath("$[0].password_hash").doesNotExist());
        mvc.perform(get(base(TRADE_FAIR, T_KICKOFF) + "/eligible-assignees").session(ana))
                .andExpect(jsonPath("$[*].member_id", contains(ACME_ANA, ACME_BEN)));

        // A deactivated project member is no longer eligible (SP-04).
        jdbc.update("UPDATE member SET status = 'DEACTIVATED' WHERE member_id = ?::uuid", ACME_VERA);
        entityManager.clear(); // the test runs in one transaction; a real request reads the database afresh
        mvc.perform(get(base(WEBSITE, T_LAYOUT) + "/eligible-assignees").session(ana))
                .andExpect(jsonPath("$[*].member_id", contains(ACME_ANA, ACME_BEN)));
        assign(ana, WEBSITE, T_LAYOUT, ACME_VERA).andExpect(status().isUnprocessableEntity());
    }

    // --- Assign, list, remove -------------------------------------------------------------------------------------

    @Test
    void severalMembersCanBeAssignedAndOneRemovedKeepsTheOthers() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        assignees(ana, WEBSITE, T_LAYOUT).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

        assign(ana, WEBSITE, T_LAYOUT, ACME_VERA, ACME_BEN)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[*].member_id", contains(ACME_BEN, ACME_VERA)))
                .andExpect(jsonPath("$[0].name").value("Ben Demo"))
                .andExpect(jsonPath("$[0].assigned_at").isNotEmpty())
                .andExpect(jsonPath("$[0].email").doesNotExist());
        assign(ana, WEBSITE, T_LAYOUT, ACME_ANA).andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(3)));

        unassign(ana, WEBSITE, T_LAYOUT, ACME_BEN).andExpect(status().isNoContent());
        assignees(signIn("vera@example.com"), WEBSITE, T_LAYOUT)
                .andExpect(jsonPath("$[*].member_id", contains(ACME_ANA, ACME_VERA)));
        assertThat(rows(T_LAYOUT)).isEqualTo(2);

        // The owner is not changed by assignments (FR-10).
        mvc.perform(get(base(WEBSITE, T_LAYOUT)).session(ana)).andExpect(jsonPath("$.owner_member_id").value(ACME_ANA));
        unassign(ana, WEBSITE, T_LAYOUT, ACME_BEN).andExpect(status().isNotFound());
        unassign(ana, WEBSITE, T_LAYOUT, "not-an-id").andExpect(status().isNotFound());
    }

    @Test
    void theSameMemberCannotBeAssignedTwice() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        assign(ana, WEBSITE, T_LAYOUT, ACME_BEN).andExpect(status().isCreated());
        assign(ana, WEBSITE, T_LAYOUT, ACME_BEN).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"))
                .andExpect(jsonPath("$.details[0].field").value("member_ids"));
        // Repeated in one request, or mixed with a new member: refused, and nothing is saved.
        assign(ana, WEBSITE, T_IMAGES, ACME_VERA, ACME_VERA).andExpect(status().isConflict());
        assign(ana, WEBSITE, T_LAYOUT, ACME_VERA, ACME_BEN).andExpect(status().isConflict());
        assertThat(rows(T_LAYOUT)).isEqualTo(1);
        assertThat(rows(T_IMAGES)).isZero();

        // The database refuses a duplicate even without the backend check (BR-22).
        assertThatThrownBy(() -> jdbc.update("INSERT INTO task_assignee (task_id, member_id) VALUES (?::uuid, ?::uuid)",
                T_LAYOUT, ACME_BEN)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyEligibleExistingMembersCanBeAssigned() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        String unknown = "c0000000-0000-0000-0000-000000000999";
        for (String bad : new String[] {ACME_CARLA, NW_CARLA, unknown, "not-an-id", ""}) {
            assign(ana, WEBSITE, T_LAYOUT, bad).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.details[0].field").value("member_ids"));
        }
        // One bad id refuses the whole request.
        assign(ana, WEBSITE, T_LAYOUT, ACME_BEN, ACME_CARLA).andExpect(status().isUnprocessableEntity());
        assignRaw(ana, WEBSITE, T_LAYOUT, "{\"member_ids\":[]}").andExpect(status().isUnprocessableEntity());
        assignRaw(ana, WEBSITE, T_LAYOUT, "{}").andExpect(status().isUnprocessableEntity());
        String many = IntStream.range(0, 21).mapToObj(i -> "\"" + ACME_BEN + "\"").collect(Collectors.joining(","));
        assignRaw(ana, WEBSITE, T_LAYOUT, "{\"member_ids\":[" + many + "]}").andExpect(status().isUnprocessableEntity());
        assignRaw(ana, WEBSITE, T_LAYOUT, "{\"member_ids\":[\"" + ACME_BEN + "\"],\"assigned_at\":\"2020-01-01T00:00:00Z\"}")
                .andExpect(status().isUnprocessableEntity());
        assertThat(rows(T_LAYOUT)).isZero();
    }

    // --- Permissions and isolation --------------------------------------------------------------------------------

    @Test
    @DisplayName("FR-22, AC-29.5, FR-05 · Team Members and Viewers read assignees but get 403 on assign and unassign; nothing changes")
    void teamMembersAndViewersCannotAssignOrRemoveButEveryMemberCanRead() throws Exception {
        assign(signIn("ana@example.com"), WEBSITE, T_IMAGES, ACME_VERA).andExpect(status().isCreated());
        for (String email : new String[] {"ben@example.com", "vera@example.com", "carla@example.com"}) {
            MockHttpSession s = signIn(email);
            assignees(s, WEBSITE, T_IMAGES).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
            mvc.perform(get(base(WEBSITE, T_IMAGES) + "/eligible-assignees").session(s)).andExpect(status().isOk());
            if (email.startsWith("carla")) {
                continue; // Workspace Administrators may assign: see workspaceAdministratorAssignsAndRemoves
            }
            assign(s, WEBSITE, T_IMAGES, ACME_BEN).andExpect(status().isForbidden());
            unassign(s, WEBSITE, T_IMAGES, ACME_VERA).andExpect(status().isForbidden());
        }
        assertThat(rows(T_IMAGES)).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-22, FR-05, PD-03 (in part) · a Workspace Administrator, also not a project member, assigns and unassigns; rows follow")
    void workspaceAdministratorAssignsAndRemoves() throws Exception {
        MockHttpSession carla = signIn("carla@example.com");
        assign(carla, WEBSITE, T_IMAGES, ACME_VERA).andExpect(status().isCreated());
        assertThat(rows(T_IMAGES)).isEqualTo(1);
        unassign(carla, WEBSITE, T_IMAGES, ACME_VERA).andExpect(status().is2xxSuccessful());
        assertThat(rows(T_IMAGES)).isZero();
    }

    @Test
    void userOfAnotherWorkspaceAndMismatchedProjectIdsGetNotFound() throws Exception {
        assign(signIn("ana@example.com"), WEBSITE, T_IMAGES, ACME_VERA).andExpect(status().isCreated());
        MockHttpSession dan = signIn("dan@example.com");
        mvc.perform(get(base(WEBSITE, T_IMAGES) + "/eligible-assignees").session(dan)).andExpect(status().isNotFound());
        assignees(dan, WEBSITE, T_IMAGES).andExpect(status().isNotFound());
        assign(dan, WEBSITE, T_IMAGES, ACME_BEN).andExpect(status().isNotFound());
        unassign(dan, WEBSITE, T_IMAGES, ACME_VERA).andExpect(status().isNotFound());

        MockHttpSession ana = signIn("ana@example.com");
        assignees(ana, WEBSITE, T_KICKOFF).andExpect(status().isNotFound());
        assign(ana, WEBSITE, T_KICKOFF, ACME_BEN).andExpect(status().isNotFound());
        unassign(ana, TRADE_FAIR, T_IMAGES, ACME_VERA).andExpect(status().isNotFound());
        assertThat(rows(T_IMAGES)).isEqualTo(1);
        assertThat(rows(T_KICKOFF)).isZero();
    }

    @Test
    void deletingATaskDeletesItsAssignments() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        assign(ana, WEBSITE, T_LAYOUT, ACME_BEN, ACME_VERA).andExpect(status().isCreated());
        mvc.perform(delete(base(WEBSITE, T_LAYOUT)).session(ana).with(csrf())).andExpect(status().isNoContent());
        assertThat(rows(T_LAYOUT)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM task_assignee a WHERE NOT EXISTS "
                + "(SELECT 1 FROM task t WHERE t.task_id = a.task_id)", Long.class)).isZero();
    }

    @Test
    void everyAssignmentOperationNeedsASession() throws Exception {
        mvc.perform(get(base(WEBSITE, T_LAYOUT) + "/eligible-assignees")).andExpect(status().isUnauthorized());
        mvc.perform(get(base(WEBSITE, T_LAYOUT) + "/assignees")).andExpect(status().isUnauthorized());
        mvc.perform(post(base(WEBSITE, T_LAYOUT) + "/assignees").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"member_ids\":[\"" + ACME_BEN + "\"]}")).andExpect(status().isUnauthorized());
        mvc.perform(delete(base(WEBSITE, T_LAYOUT) + "/assignees/" + ACME_BEN).with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
