package com.matriz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/**
 * Task comments through direct API requests (INC-05 Feature B; openapi.yaml 0.6.0-draft; FR-16, US-15, BR-13,
 * EC-08; INC-14: Workspace Administrators who are project members may comment, PD-03 in part, PD-20). Demo data: "Review homepage images" has two comments, by Ana (09:40) and Ben (14:05) on 2026-11-03.
 */
class CommentApiTest extends ApiTestSupport {

    static final String T_KICKOFF = "e0000000-0000-0000-0000-000000000004";
    static final String C_ANA = "f0000000-0000-0000-0000-000000000001";
    static final String C_BEN = "f0000000-0000-0000-0000-000000000002";

    private static String path(String project, String task) {
        return "/projects/" + project + "/tasks/" + task + "/comments";
    }

    private ResultActions postJson(MockHttpSession s, String project, String task, String json) throws Exception {
        return mvc.perform(post(path(project, task))
                .session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions say(MockHttpSession s, String project, String task, String text) throws Exception {
        return postJson(s, project, task, "{\"body\":\"" + text + "\"}");
    }

    private long count(String task) {
        return jdbc.queryForObject("SELECT count(*) FROM comment WHERE task_id = ?::uuid", Long.class, task);
    }

    // --- Listing ---------------------------------------------------------------------------------------------------

    @Test
    void commentsAreListedOldestFirstWithAuthorNameAndDateButNoAccountData() throws Exception {
        mvc.perform(get(path(WEBSITE, T_IMAGES)).session(signIn("vera@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].comment_id", contains(C_ANA, C_BEN)))
                .andExpect(jsonPath("$[0].author_member_id").value(ACME_ANA))
                .andExpect(jsonPath("$[0].author_name").value("Ana Demo"))
                .andExpect(jsonPath("$[0].created_at").value("2026-11-03T09:40:00Z"))
                .andExpect(jsonPath("$[0].body").value("Please use the photos from the shared folder."))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].author_email").doesNotExist());
        mvc.perform(get(path(WEBSITE, T_LAYOUT)).session(signIn("carla@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    // --- Adding: author, date and content rules -------------------------------------------------------------------

    @Test
    void theServerSetsAuthorAndDateAndTheCommentIsListedLast() throws Exception {
        Instant before = Instant.now().minusSeconds(1);
        MockHttpSession ben = signIn("ben@example.com");
        String body = say(ben, WEBSITE, T_IMAGES, "  Sections three and four are done.  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.task_id").value(T_IMAGES))
                .andExpect(jsonPath("$.author_member_id").value(ACME_BEN))
                .andExpect(jsonPath("$.author_name").value("Ben Demo"))
                .andExpect(jsonPath("$.body").value("Sections three and four are done."))
                .andReturn().getResponse().getContentAsString();
        Instant created = Instant.parse(JsonPath.read(body, "$.created_at"));
        assertThat(created).isAfter(before).isBefore(Instant.now().plusSeconds(1));
        String id = JsonPath.read(body, "$.comment_id");

        mvc.perform(get(path(WEBSITE, T_IMAGES)).session(signIn("ana@example.com")))
                .andExpect(jsonPath("$[*].comment_id", contains(C_ANA, C_BEN, id)));
        assertThat(jdbc.queryForObject("SELECT author_member_id::text FROM comment WHERE comment_id = ?::uuid",
                String.class, id)).isEqualTo(ACME_BEN);
    }

    @Test
    void clientSuppliedAuthorOrDateIsRefusedAndNothingIsSaved() throws Exception {
        MockHttpSession ben = signIn("ben@example.com");
        postJson(ben, WEBSITE, T_IMAGES, "{\"body\":\"Spoofed\",\"author_member_id\":\"" + ACME_ANA + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("author_member_id"));
        postJson(ben, WEBSITE, T_IMAGES, "{\"body\":\"Backdated\",\"created_at\":\"2020-01-01T00:00:00Z\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("created_at"));
        postJson(ben, WEBSITE, T_IMAGES, "{\"body\":").andExpect(status().isBadRequest());
        assertThat(count(T_IMAGES)).isEqualTo(2);
    }

    @Test
    void contentMustBe1To2000CharactersAfterTrimming() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        say(ana, WEBSITE, T_LAYOUT, "").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.details[0].field").value("body"))
                .andExpect(jsonPath("$.details[0].issue").value("A comment cannot be empty."));
        say(ana, WEBSITE, T_LAYOUT, "     ").andExpect(status().isUnprocessableEntity());
        say(ana, WEBSITE, T_LAYOUT, "\\t \\n ").andExpect(status().isUnprocessableEntity());
        postJson(ana, WEBSITE, T_LAYOUT, "{\"body\":null}").andExpect(status().isUnprocessableEntity());
        postJson(ana, WEBSITE, T_LAYOUT, "{}").andExpect(status().isUnprocessableEntity());
        assertThat(count(T_LAYOUT)).isZero();

        say(ana, WEBSITE, T_LAYOUT, "k").andExpect(status().isCreated()).andExpect(jsonPath("$.body").value("k"));
        say(ana, WEBSITE, T_LAYOUT, "c".repeat(2000)).andExpect(status().isCreated());
        say(ana, WEBSITE, T_LAYOUT, "  " + "c".repeat(2000) + "  ").andExpect(status().isCreated());
        say(ana, WEBSITE, T_LAYOUT, "c".repeat(2001)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].issue").value("A comment can be at most 2000 characters long."));
        assertThat(count(T_LAYOUT)).isEqualTo(3);
    }

    // --- Permissions and isolation --------------------------------------------------------------------------------

    @Test
    @DisplayName("FR-16, FR-05, PD-03 (in part) · a Workspace Administrator who is a project member comments: 201, author is her member id")
    void workspaceAdministratorProjectMemberComments() throws Exception {
        jdbc.update("INSERT INTO project_member (project_id, member_id) VALUES (?::uuid, ?::uuid)", WEBSITE,
                "c0000000-0000-0000-0000-000000000001");
        say(signIn("carla@example.com"), WEBSITE, T_IMAGES, "Fictitious admin comment")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author_member_id").value("c0000000-0000-0000-0000-000000000001"));
    }

    @Test
    void viewersAndNonProjectMembersCannotComment() throws Exception {
        say(signIn("vera@example.com"), WEBSITE, T_IMAGES, "Viewer").andExpect(status().isForbidden());
        say(signIn("carla@example.com"), WEBSITE, T_IMAGES, "WA who is not a project member, PD-20").andExpect(status().isForbidden());
        // Ben stops taking part in Trade fair stand: he can still read its comments but not add one (provisional).
        jdbc.update("DELETE FROM project_member WHERE project_id = ?::uuid AND member_id = ?::uuid", TRADE_FAIR, ACME_BEN);
        MockHttpSession ben = signIn("ben@example.com");
        mvc.perform(get(path(TRADE_FAIR, T_KICKOFF)).session(ben)).andExpect(status().isOk());
        say(ben, TRADE_FAIR, T_KICKOFF, "Not a member").andExpect(status().isForbidden());
        assertThat(count(T_IMAGES)).isEqualTo(2);
        assertThat(count(T_KICKOFF)).isZero();
    }

    @Test
    void userOfAnotherWorkspaceAndMismatchedProjectIdsGetNotFound() throws Exception {
        MockHttpSession dan = signIn("dan@example.com");
        mvc.perform(get(path(WEBSITE, T_IMAGES)).session(dan)).andExpect(status().isNotFound());
        say(dan, WEBSITE, T_IMAGES, "Intruder").andExpect(status().isNotFound());
        // Through his own project, a foreign task id is not found either.
        mvc.perform(get(path(LAB, T_IMAGES)).session(dan)).andExpect(status().isNotFound());
        say(dan, LAB, T_IMAGES, "Intruder").andExpect(status().isNotFound());

        MockHttpSession ana = signIn("ana@example.com");
        mvc.perform(get(path(TRADE_FAIR, T_IMAGES)).session(ana)).andExpect(status().isNotFound());
        say(ana, TRADE_FAIR, T_IMAGES, "Wrong project").andExpect(status().isNotFound());
        mvc.perform(get(path(WEBSITE, "not-an-id")).session(ana)).andExpect(status().isNotFound());
        assertThat(count(T_IMAGES)).isEqualTo(2);
    }

    @Test
    void everyCommentOperationNeedsASession() throws Exception {
        mvc.perform(get(path(WEBSITE, T_IMAGES))).andExpect(status().isUnauthorized());
        mvc.perform(post(path(WEBSITE, T_IMAGES)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"No session\"}")).andExpect(status().isUnauthorized());
    }
}
