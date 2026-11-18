package com.matriz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import com.matriz.accounts.AttemptLimiter;

/**
 * Rate limits of sign-up and sign-in (INC-12; NFR-11, SP-08; US-22, US-01; AC-22.6, EC-16; AC-INC12.5). The limits
 * are lowered for this class only (sign-up 5, sign-in 3 per IP and email and 5 per IP, to keep the tests short; the
 * documented values 5, 10 and 50 are the defaults in application.properties and are not changed here). The counters
 * are cleared before and after each test, so the tests are independent. The window expiry is tested in
 * AttemptLimiterTest with a controllable clock.
 */
@TestPropertySource(properties = {
        "matriz.rate-limit.sign-up.max=5",
        "matriz.rate-limit.sign-in.max-per-ip-and-email=3",
        "matriz.rate-limit.sign-in.max-per-ip=5"})
class SignUpRateLimitTest extends ApiTestSupport {

    @Autowired
    AttemptLimiter limiter;

    @BeforeEach
    @AfterEach
    void clearCounters() {
        limiter.clear();
    }

    private ResultActions signUp(String email, String ip) throws Exception {
        return mvc.perform(post("/auth/sign-up").with(csrf()).with(r -> {
            r.setRemoteAddr(ip);
            return r;
        }).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nia\",\"email\":\"" + email + "\",\"password\":\"fictitious-nia-pw-1\"}"));
    }

    private ResultActions signIn(String email, String password, String ip) throws Exception {
        return mvc.perform(post("/auth/sign-in").with(csrf()).with(r -> {
            r.setRemoteAddr(ip);
            return r;
        }).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private int accounts() {
        return jdbc.queryForObject("SELECT count(*) FROM account", Integer.class);
    }

    private void assertTooMany(ResultActions result) throws Exception {
        result.andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("too_many_requests"))
                .andExpect(header().exists("Retry-After"));
        // Retry-After is a whole number of seconds, at least 1.
        long seconds = Long.parseLong(result.andReturn().getResponse().getHeader("Retry-After"));
        assertThat(seconds).isGreaterThanOrEqualTo(1);
    }

    // --- Sign-up ---------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-22.6 · EC-16 · AC-INC12.5 · the sixth sign-up from one IP in the hour gets 429 too_many_requests with Retry-After and creates nothing")
    void sixthSignUpIsRefused() throws Exception {
        for (int i = 1; i <= 5; i++) {
            signUp("user" + i + "@example.com", "10.0.0.1").andExpect(status().isCreated());
        }
        int count = accounts();

        assertTooMany(signUp("user6@example.com", "10.0.0.1"));

        assertThat(accounts()).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM account WHERE email = 'user6@example.com'",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("AC-22.6 · every sign-up request counts, including refused ones: 5 invalid requests use up the limit")
    void refusedSignUpsAlsoCount() throws Exception {
        for (int i = 1; i <= 5; i++) {
            signUp("not-an-email", "10.0.0.2").andExpect(status().isUnprocessableEntity());
        }
        assertTooMany(signUp("valid@example.com", "10.0.0.2"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM account WHERE email = 'valid@example.com'",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("AC-22.6 · the sign-up limit is per client address: another address is not affected")
    void signUpLimitIsPerAddress() throws Exception {
        for (int i = 1; i <= 5; i++) {
            signUp("user" + i + "@example.com", "10.0.0.3").andExpect(status().isCreated());
        }
        assertTooMany(signUp("user6@example.com", "10.0.0.3"));
        signUp("user7@example.com", "10.0.0.4").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("AC-22.6 · after clear the counters start again (the same state as a restart of the application)")
    void countersStartAgainAfterClear() throws Exception {
        for (int i = 1; i <= 5; i++) {
            signUp("user" + i + "@example.com", "10.0.0.5").andExpect(status().isCreated());
        }
        assertTooMany(signUp("user6@example.com", "10.0.0.5"));
        limiter.clear();
        signUp("user6@example.com", "10.0.0.5").andExpect(status().isCreated());
    }

    // --- Sign-in ---------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-22.6 · EC-16 · AC-INC12.5 · after the failure limit for one IP and email, even the correct password gets 429 with Retry-After")
    void perIpAndEmailLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            signIn("ana@example.com", "wrong-password-1", "10.0.1.1")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("invalid_credentials"));
        }
        assertTooMany(signIn("ana@example.com", TEST_PASSWORD, "10.0.1.1"));
        // The same email in another case or with spaces is the same key.
        assertTooMany(signIn(" ANA@example.com ", TEST_PASSWORD, "10.0.1.1"));
        assertTooMany(signIn("ana@example.com", "wrong-password-1", "10.0.1.1"));
    }

    @Test
    @DisplayName("AC-22.6 · the per IP and email limit does not block another email from the same IP, nor the same email from another IP")
    void perIpAndEmailLimitIsNarrow() throws Exception {
        for (int i = 0; i < 3; i++) {
            signIn("ana@example.com", "wrong-password-1", "10.0.1.2").andExpect(status().isUnauthorized());
        }
        assertTooMany(signIn("ana@example.com", TEST_PASSWORD, "10.0.1.2"));

        signIn("ben@example.com", TEST_PASSWORD, "10.0.1.2").andExpect(status().isOk());
        signIn("ana@example.com", TEST_PASSWORD, "10.0.1.3").andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC-22.6 · EC-16 · after the failure limit for one IP over different emails, any sign-in from that IP gets 429")
    void perIpLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            signIn("nobody" + i + "@example.com", "wrong-password-1", "10.0.2.1")
                    .andExpect(status().isUnauthorized());
        }
        assertTooMany(signIn("ana@example.com", TEST_PASSWORD, "10.0.2.1"));
        // Another address is not affected.
        signIn("ana@example.com", TEST_PASSWORD, "10.0.2.2").andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC-22.6 · a successful sign-in does not reset the failure counters")
    void successDoesNotResetCounters() throws Exception {
        signIn("ana@example.com", "wrong-password-1", "10.0.3.1").andExpect(status().isUnauthorized());
        signIn("ana@example.com", "wrong-password-1", "10.0.3.1").andExpect(status().isUnauthorized());
        signIn("ana@example.com", TEST_PASSWORD, "10.0.3.1").andExpect(status().isOk());
        // Third failure still counts toward the same window, so the limit of 3 is now reached.
        signIn("ana@example.com", "wrong-password-1", "10.0.3.1").andExpect(status().isUnauthorized());
        assertTooMany(signIn("ana@example.com", TEST_PASSWORD, "10.0.3.1"));
    }

    @Test
    @DisplayName("AC-22.6 · successful sign-ins are not counted: many correct sign-ins never hit the limit")
    void successfulSignInsAreNotCounted() throws Exception {
        for (int i = 0; i < 8; i++) {
            signIn("ana@example.com", TEST_PASSWORD, "10.0.3.2").andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("AC-22.6 · sign-in failures do not use the sign-up allowance, and sign-ups do not use the sign-in allowance")
    void signUpAndSignInCountersAreSeparate() throws Exception {
        for (int i = 0; i < 3; i++) {
            signIn("ana@example.com", "wrong-password-1", "10.0.4.1").andExpect(status().isUnauthorized());
        }
        signUp("nia@example.com", "10.0.4.1").andExpect(status().isCreated());

        for (int i = 2; i <= 5; i++) {
            signUp("nia" + i + "@example.com", "10.0.4.2").andExpect(status().isCreated());
        }
        signIn("ben@example.com", TEST_PASSWORD, "10.0.4.2").andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC-22.6 · a sign-in refused with 429 creates no session")
    void refusedSignInCreatesNoSession() throws Exception {
        for (int i = 0; i < 3; i++) {
            signIn("ana@example.com", "wrong-password-1", "10.0.5.1").andExpect(status().isUnauthorized());
        }
        var result = signIn("ana@example.com", TEST_PASSWORD, "10.0.5.1").andExpect(status().isTooManyRequests())
                .andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    @DisplayName("AC-22.6 · EC-14 · audit S-07 · sign-ins that can match no account (over 72 bytes, invalid email, control character) count as failures")
    void unmatchableSignInsCountAsFailures() throws Exception {
        signIn("ana@example.com", "\u00e4".repeat(37), "10.0.6.1").andExpect(status().isUnauthorized());
        signIn("ana@example.com", "\u00e4".repeat(37), "10.0.6.1").andExpect(status().isUnauthorized());
        signIn("ana@example.com", "p".repeat(80), "10.0.6.1").andExpect(status().isUnauthorized());
        assertTooMany(signIn("ana@example.com", TEST_PASSWORD, "10.0.6.1"));

        for (int i = 0; i < 5; i++) {
            signIn("not-an-email" + i, "wrong-password-1", "10.0.6.2").andExpect(status().isUnauthorized());
        }
        assertTooMany(signIn("ben@example.com", TEST_PASSWORD, "10.0.6.2"));
    }

    @Test
    @DisplayName("AC-22.6 · SP-08 · TD-13 · X-Forwarded-For, X-Real-IP and Forwarded headers do not change the sign-up rate-limit key")
    void forwardingHeadersDoNotChangeTheKey() throws Exception {
        for (int i = 1; i <= 5; i++) {
            int n = i;
            mvc.perform(post("/auth/sign-up").with(csrf()).with(r -> {
                r.setRemoteAddr("10.0.7.1");
                r.addHeader("X-Forwarded-For", "203.0.113." + n);
                r.addHeader("X-Real-IP", "203.0.114." + n);
                r.addHeader("Forwarded", "for=203.0.115." + n);
                return r;
            }).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Nia\",\"email\":\"fwd" + n
                            + "@example.com\",\"password\":\"fictitious-nia-pw-1\"}"))
                    .andExpect(status().isCreated());
        }
        assertTooMany(mvc.perform(post("/auth/sign-up").with(csrf()).with(r -> {
            r.setRemoteAddr("10.0.7.1");
            r.addHeader("X-Forwarded-For", "198.51.100.77");
            r.addHeader("X-Real-IP", "198.51.100.78");
            r.addHeader("Forwarded", "for=198.51.100.79");
            return r;
        }).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nia\",\"email\":\"fwd6@example.com\",\"password\":\"fictitious-nia-pw-1\"}")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM account WHERE email = 'fwd6@example.com'",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("AC-22.6 · SP-08 · TD-13 · a forwarded address cannot be used to avoid the sign-in failure limit")
    void forwardingHeadersDoNotEvadeSignInLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            int n = i;
            mvc.perform(post("/auth/sign-in").with(csrf()).with(r -> {
                r.setRemoteAddr("10.0.7.2");
                r.addHeader("X-Forwarded-For", "203.0.113." + n);
                return r;
            }).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"ana@example.com\",\"password\":\"wrong-password-1\"}"))
                    .andExpect(status().isUnauthorized());
        }
        assertTooMany(mvc.perform(post("/auth/sign-in").with(csrf()).with(r -> {
            r.setRemoteAddr("10.0.7.2");
            r.addHeader("X-Forwarded-For", "198.51.100.1");
            return r;
        }).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@example.com\",\"password\":\"" + TEST_PASSWORD + "\"}")));
    }
}
