package com.matriz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;

/**
 * Sign-in UI rework (approved 2026-11-12): the interface configuration served as GET /config.js (CMP-11). Only GET
 * /config.js is open without a session; every other operation still needs one (SP-01, FR-01). The base URL is a
 * same-site path only (TD-03).
 */
class ClientConfigApiTest extends ApiTestSupport {

    private static final MediaType JAVASCRIPT = MediaType.valueOf("application/javascript");

    @Autowired
    Environment environment;

    @Test
    @DisplayName("SP-01 · GET /config.js without a session returns 200 with the default empty apiBaseUrl")
    void configScriptIsOpenAndHasTheDefaultBaseUrl() throws Exception {
        mvc.perform(get("/config.js"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(JAVASCRIPT))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().string("window.MATRIZ_CONFIG = Object.freeze({ apiBaseUrl: \"\" });\n"));
    }

    @Test
    @DisplayName("SP-01 · GET /config.js exposes no secrets")
    void configScriptContainsNoSecrets() throws Exception {
        String body = mvc.perform(get("/config.js"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Compared only, never printed.
        String dbUrl = environment.getProperty("spring.datasource.url");
        String dbUser = environment.getProperty("spring.datasource.username");
        String dbPassword = environment.getProperty("spring.datasource.password");

        assertThat(body.toLowerCase()).doesNotContain("password").doesNotContain("jdbc");
        assertThat(body.contains(TEST_PASSWORD)).as("contains the test password").isFalse();
        if (dbUrl != null && !dbUrl.isBlank()) {
            assertThat(body.contains(dbUrl)).as("contains the datasource URL").isFalse();
        }
        if (dbUser != null && !dbUser.isBlank()) {
            assertThat(body.contains(dbUser)).as("contains the datasource user name").isFalse();
        }
        if (dbPassword != null && !dbPassword.isBlank()) {
            assertThat(body.contains(dbPassword)).as("contains the datasource password").isFalse();
        }
    }

    @Test
    @DisplayName("SP-01 · POST /config.js without a session is not opened up")
    void postToConfigScriptIsRefused() throws Exception {
        mvc.perform(post("/config.js").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("not_authenticated"));
        mvc.perform(post("/config.js"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("csrf_invalid"));
    }

    @Test
    @DisplayName("SP-01, FR-01 · protected operations still need a session after the /config.js change")
    void protectedOperationsStillNeedASession() throws Exception {
        mvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("not_authenticated"));
        mvc.perform(get("/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("not_authenticated"));
    }
}
