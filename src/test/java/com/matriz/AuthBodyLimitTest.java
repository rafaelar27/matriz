package com.matriz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Size limit of 16384 bytes for request bodies under /auth/ (INC-12; NFR-11, SP-08; US-22, US-01; AC-22.6; decided
 * 2026-11-18, security audit S-04; limit of the decision, not of the user stories). A declared length over the limit
 * gets 413 payload_too_large; a normal body is unaffected. A body without a declared length is tested in
 * AuthBodyLimitFilterTest, because MockMvc always declares the length.
 */
class AuthBodyLimitTest extends ApiTestSupport {

    private static final int LIMIT = 16384;
    private static final String SECRET = "body-limit-canary-pw-9";

    /** A valid JSON body of exactly {@code size} bytes: the object followed by spaces. */
    private static byte[] body(String object, int size) {
        byte[] base = object.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[size];
        java.util.Arrays.fill(out, (byte) ' ');
        System.arraycopy(base, 0, out, 0, base.length);
        return out;
    }

    private static String signUpJson() {
        return "{\"name\":\"Nia\",\"email\":\"nia@example.com\",\"password\":\"" + SECRET + "\"}";
    }

    private static String signInJson() {
        return "{\"email\":\"ana@example.com\",\"password\":\"" + SECRET + "\"}";
    }

    private ResultActions send(String uri, byte[] bytes) throws Exception {
        return mvc.perform(post(uri).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(bytes));
    }

    @Test
    @DisplayName("AC-22.6 · NFR-11 · SP-08 · a sign-up body of 16384 bytes is accepted")
    void signUpAtTheLimitIsAccepted() throws Exception {
        send("/auth/sign-up", body(signUpJson(), LIMIT)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("AC-22.6 · NFR-11 · SP-08 · a sign-up body of 16385 bytes gets 413 payload_too_large, creates nothing and does not echo the password")
    void signUpOverTheLimitGets413() throws Exception {
        int count = jdbc.queryForObject("SELECT count(*) FROM account", Integer.class);

        send("/auth/sign-up", body(signUpJson(), LIMIT + 1))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("payload_too_large"))
                .andExpect(content().string(not(containsString(SECRET))))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString(" at com."))));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM account", Integer.class)).isEqualTo(count);
    }

    @Test
    @DisplayName("AC-22.6 · NFR-11 · SP-08 · a sign-in body of 16384 bytes is processed and one of 16385 bytes gets 413 payload_too_large without echo")
    void signInLimit() throws Exception {
        send("/auth/sign-in", body(signInJson(), LIMIT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"));

        send("/auth/sign-in", body(signInJson(), LIMIT + 1))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("payload_too_large"))
                .andExpect(content().string(not(containsString(SECRET))))
                .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    @DisplayName("AC-22.1 · a normal sign-up body is unaffected by the limit")
    void normalBodyIsUnaffected() throws Exception {
        send("/auth/sign-up", signUpJson().getBytes(StandardCharsets.UTF_8)).andExpect(status().isCreated());
    }
}
