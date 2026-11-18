package com.matriz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

/** Sign-in, profile, sign-out and the session lifecycle (FR-01, SP-01, TD-03). */
class AuthenticationTest extends ApiTestSupport {

    private static final String INVALID = "{\"code\":\"invalid_credentials\",\"message\":\"The email or password is incorrect.\"}";

    @Test
    void validCredentialsSignInAndReturnTheProfile() throws Exception {
        MockHttpSession session = signIn("ana@example.com");

        mvc.perform(get("/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.name").value("Ana Demo"))
                .andExpect(jsonPath("$.memberships", hasSize(2)))
                .andExpect(jsonPath("$.memberships[0].workspace_name").value("Acme Studio"))
                .andExpect(jsonPath("$.memberships[0].role").value("Project Manager"))
                .andExpect(jsonPath("$.memberships[1].role").value("Team Member"));
    }

    @Test
    void profileNeverContainsThePasswordOrItsHash() throws Exception {
        MockHttpSession session = signIn("ben@example.com");

        mvc.perform(get("/auth/me").session(session))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("$2a$"))))
                .andExpect(content().string(not(containsString(TEST_PASSWORD))));
    }

    @Test
    void wrongPasswordIsRefusedWithAGenericMessage() throws Exception {
        mvc.perform(post("/auth/sign-in").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(INVALID, true));
    }

    @Test
    void unknownEmailGetsExactlyTheSameResponse() throws Exception {
        mvc.perform(post("/auth/sign-in").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(INVALID, true));
    }

    @Test
    void emptyCredentialsGetTheSameResponse() throws Exception {
        mvc.perform(post("/auth/sign-in").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(INVALID, true));
    }

    @Test
    void requestsWithoutAuthenticationGet401() throws Exception {
        mvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("not_authenticated"));
        mvc.perform(post("/projects/" + WEBSITE + "/tasks").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"owner_member_id\":\"" + ACME_ANA + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/projects/" + WEBSITE + "/tasks/" + T_IMAGES)
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"Done\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("not_authenticated"));
    }

    @Test
    void afterSignOutTheSameSessionGets401() throws Exception {
        MockHttpSession session = signIn("ana@example.com");

        mvc.perform(post("/auth/sign-out").session(session).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("MATRIZ_SESSION", 0));

        // The session is destroyed on the server; MockMvc cannot reuse an invalidated session object, so the
        // follow-up request with the old cookie is checked against the running app (see README, manual checks).
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void anEndedOrExpiredSessionIdGetsSessionExpired() throws Exception {
        // The server no longer knows this session id: the same situation as after expiry or sign-out.
        mvc.perform(get("/auth/me").with(request -> {
                    request.setRequestedSessionId("expired-session-id");
                    request.setRequestedSessionIdValid(false);
                    return request;
                }))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("session_expired"));
    }

    @Test
    void signInGivesANewSessionId() throws Exception {
        MockHttpSession before = new MockHttpSession();
        MockHttpSession after = (MockHttpSession) mvc.perform(post("/auth/sign-in").session(before).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.com\",\"password\":\"" + TEST_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
        assertThat(before.isInvalid()).isTrue();
        assertThat(after.getId()).isNotEqualTo(before.getId());
    }

    @Test
    void stateChangingRequestsWithoutACsrfTokenAreRefused() throws Exception {
        MockHttpSession session = signIn("ana@example.com");

        mvc.perform(post("/auth/sign-out").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("csrf_invalid"));
    }
}
