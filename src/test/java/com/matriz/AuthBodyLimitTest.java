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
import org.springframework.mock.web.MockHttpServletRequest;
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

    /**
     * A valid JSON body of exactly {@code size} bytes: the object with spaces before its closing brace, so a parser
     * has to read to the end of the body.
     */
    private static byte[] body(String object, int size) {
        String open = object.substring(0, object.length() - 1);
        byte[] head = open.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[size];
        java.util.Arrays.fill(out, (byte) ' ');
        System.arraycopy(head, 0, out, 0, head.length);
        out[size - 1] = '}';
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

    /** A request whose length is not declared (like a chunked body): MockMvc itself always declares it. */
    private ResultActions sendUndeclared(String uri, byte[] bytes) throws Exception {
        return mvc.perform(context -> {
            MockHttpServletRequest request = new MockHttpServletRequest(context, "POST", uri) {
                @Override
                public long getContentLengthLong() {
                    return -1;
                }

                @Override
                public int getContentLength() {
                    return -1;
                }
            };
            request.setServletPath(uri);
            request.setContentType(MediaType.APPLICATION_JSON_VALUE);
            request.setContent(bytes);
            return csrf().postProcessRequest(request);
        });
    }

    @Test
    @DisplayName("AC-22.6 · NFR-11 · SP-08 · a sign-up body without a declared length is read up to 16384 bytes and over that gets 413 payload_too_large, creates nothing and does not echo")
    void undeclaredSignUpOverTheLimitGets413() throws Exception {
        sendUndeclared("/auth/sign-up", body(signUpJson(), LIMIT)).andExpect(status().isCreated());

        int count = jdbc.queryForObject("SELECT count(*) FROM account", Integer.class);
        sendUndeclared("/auth/sign-up", body(signUpJson().replace("nia@", "nia2@"), LIMIT + 1))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("payload_too_large"))
                .andExpect(content().string(not(containsString(SECRET))))
                .andExpect(content().string(not(containsString("password"))));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM account", Integer.class)).isEqualTo(count);
    }

    @Test
    @DisplayName("AC-22.6 · NFR-11 · SP-08 · a sign-in body without a declared length: 16384 bytes is processed, 16385 gets 413 payload_too_large")
    void undeclaredSignInOverTheLimitGets413() throws Exception {
        sendUndeclared("/auth/sign-in", body(signInJson(), LIMIT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"));
        sendUndeclared("/auth/sign-in", body(signInJson(), LIMIT + 1))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("payload_too_large"))
                .andExpect(content().string(not(containsString(SECRET))));
    }

    @Test
    @DisplayName("API-A2 · a normal malformed JSON body on an /auth/ route is still 400 malformed_request")
    void malformedJsonStays400() throws Exception {
        sendUndeclared("/auth/sign-up", "{\"name\": broken".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed_request"));
        send("/auth/sign-in", "not json".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed_request"));
    }
}
