package com.matriz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Public sign-up through direct API requests (INC-12; FR-01, NFR-11, BR-24, SP-01, SP-09, SP-10; US-22: AC-22.1 to
 * AC-22.5, AC-22.7, AC-22.8, EC-13 to EC-15; AC-INC12.1 to AC-INC12.4, AC-INC12.6, AC-INC12.7). Rate limits are in
 * SignUpRateLimitTest, the simultaneous duplicate (EC-12) in SignUpConcurrencyTest. Decisions of 2026-11-18 in the
 * decision log. The email length limit of 254 is an implementation limit, not in the docs (open decision).
 */
@ExtendWith(OutputCaptureExtension.class)
class SignUpApiTest extends ApiTestSupport {

    static final String NIA_PASSWORD = "fictitious-nia-pw-1";

    @Autowired
    ObjectMapper json;

    private ResultActions signUp(MockHttpSession session, Map<String, Object> body) throws Exception {
        var request = post("/auth/sign-up").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
        if (session != null) {
            request.session(session);
        }
        return mvc.perform(request);
    }

    private ResultActions signUp(String name, String email, String password) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("email", email);
        body.put("password", password);
        return signUp(null, body);
    }

    private ResultActions signInAs(String email, String password) throws Exception {
        Map<String, Object> body = Map.of("email", email, "password", password);
        return mvc.perform(post("/auth/sign-in").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private int accounts() {
        return jdbc.queryForObject("SELECT count(*) FROM account", Integer.class);
    }

    private int accountsWithEmail(String email) {
        return jdbc.queryForObject("SELECT count(*) FROM account WHERE lower(email) = lower(?)", Integer.class,
                email.trim());
    }

    private void assertRefused(ResultActions result, String field) throws Exception {
        result.andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.details[?(@.field == '" + field + "')]", hasSize(1)));
    }

    // --- Success (AC-22.1, AC-22.8, AC-INC12.1) ------------------------------------------------------------------

    @Test
    @DisplayName("AC-22.1 · AC-INC12.1 · sign-up creates the account, signs it in with new session and token, returns the profile")
    void signUpCreatesAndSignsIn() throws Exception {
        MockHttpSession before = new MockHttpSession();
        int count = accounts();

        var result = signUp(before, nameEmailPassword("Nia Newcomer", "nia@example.com", NIA_PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.account_id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Nia Newcomer"))
                .andExpect(jsonPath("$.email").value("nia@example.com"))
                .andExpect(jsonPath("$.memberships", hasSize(0)))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();

        assertThat(accounts()).isEqualTo(count + 1);
        MockHttpSession after = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(after).isNotNull();
        assertThat(after.getId()).isNotEqualTo(before.getId());
        assertThat(before.isInvalid()).isTrue();

        mvc.perform(get("/auth/me").session(after))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("nia@example.com"))
                .andExpect(jsonPath("$.memberships", hasSize(0)));
    }

    @Test
    @DisplayName("AC-22.8 · a new account has no membership and no role")
    void newAccountHasNoMembership() throws Exception {
        signUp("Nia Newcomer", "nia@example.com", NIA_PASSWORD).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM member m JOIN account a ON a.account_id = m.account_id "
                + "WHERE a.email = 'nia@example.com'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("AC-INC12.1 · SP-10 · the response never contains the password or a hash")
    void responseNeverContainsPasswordOrHash() throws Exception {
        signUp("Nia Newcomer", "nia@example.com", NIA_PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(content().string(not(containsString(NIA_PASSWORD))))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("$2"))));
    }

    @Test
    @DisplayName("AC-INC12.1 · sign-up without a CSRF token is refused with 403 csrf_invalid and creates nothing")
    void signUpNeedsCsrfToken() throws Exception {
        int count = accounts();
        mvc.perform(post("/auth/sign-up").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(nameEmailPassword("Nia Newcomer", "nia@example.com", NIA_PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("csrf_invalid"));
        assertThat(accounts()).isEqualTo(count);
    }

    @Test
    @DisplayName("AC-22.1 · SP-10 · the stored password is a BCrypt hash, not the password")
    void storedPasswordIsABCryptHash() throws Exception {
        signUp("Nia Newcomer", "nia@example.com", NIA_PASSWORD).andExpect(status().isCreated());

        String stored = jdbc.queryForObject("SELECT password_hash FROM account WHERE email = 'nia@example.com'",
                String.class);
        assertThat(stored).startsWith("$2").isNotEqualTo(NIA_PASSWORD).doesNotContain(NIA_PASSWORD);
    }

    @Test
    @DisplayName("AC-22.1 · the account id is chosen by the server and is read back by a later sign-in with a new session")
    void dataIsReadBackByANewSession() throws Exception {
        String id = com.jayway.jsonpath.JsonPath.read(
                signUp("Nia Newcomer", "nia@example.com", NIA_PASSWORD).andReturn().getResponse().getContentAsString(),
                "$.account_id");

        signInAs("nia@example.com", NIA_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account_id").value(id))
                .andExpect(jsonPath("$.name").value("Nia Newcomer"));
        assertThat(jdbc.queryForObject("SELECT account_id::text FROM account WHERE email = 'nia@example.com'",
                String.class)).isEqualTo(id);
    }

    // --- Name (AC-22.2) ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-22.2 · a name of 1 character and a name of 80 characters are accepted")
    void nameBoundariesAccepted() throws Exception {
        signUp("N", "one@example.com", NIA_PASSWORD).andExpect(status().isCreated());
        signUp("N".repeat(80), "eighty@example.com", NIA_PASSWORD).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("AC-22.2 · a name of 81 characters, an empty name and a whitespace-only name are refused at the name field")
    void nameOutOfRangeRefused() throws Exception {
        int count = accounts();
        assertRefused(signUp("N".repeat(81), "a@example.com", NIA_PASSWORD), "name");
        assertRefused(signUp("", "a@example.com", NIA_PASSWORD), "name");
        assertRefused(signUp("   \t ", "a@example.com", NIA_PASSWORD), "name");
        assertThat(accounts()).isEqualTo(count);
    }

    @Test
    @DisplayName("AC-22.2 · the name is trimmed before the limit and before storing")
    void nameIsTrimmed() throws Exception {
        signUp("  " + "N".repeat(80) + "  ", "a@example.com", NIA_PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("N".repeat(80)));
        assertRefused(signUp("  " + "N".repeat(81) + "  ", "b@example.com", NIA_PASSWORD), "name");
        assertThat(jdbc.queryForObject("SELECT name FROM account WHERE email = 'a@example.com'", String.class))
                .isEqualTo("N".repeat(80));
    }

    @Test
    @DisplayName("AC-22.2 · the name length counts characters (code points), not UTF-16 units")
    void nameCountsCodePoints() throws Exception {
        String eighty = "😀".repeat(80);
        signUp(eighty, "emoji@example.com", NIA_PASSWORD).andExpect(status().isCreated());
        assertRefused(signUp("😀".repeat(81), "emoji2@example.com", NIA_PASSWORD), "name");
    }

    @Test
    @DisplayName("AC-22.2 · a missing name or a null name is refused at the name field")
    void missingNameRefused() throws Exception {
        assertRefused(signUp(null, Map.of("email", "a@example.com", "password", NIA_PASSWORD)), "name");
        assertRefused(signUp(null, "a@example.com", NIA_PASSWORD), "name");
    }

    // --- Email (AC-22.2) -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-22.2 · an email is stored trimmed and in lower case, and returned that way")
    void emailIsTrimmedAndLowerCased() throws Exception {
        signUp("Nia", "  Nia.Newcomer@Example.COM \t", NIA_PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("nia.newcomer@example.com"));
        assertThat(jdbc.queryForObject("SELECT email FROM account WHERE lower(email) = 'nia.newcomer@example.com'",
                String.class)).isEqualTo("nia.newcomer@example.com");
    }

    @Test
    @DisplayName("AC-22.2 · an email without exactly one @ with text on both sides, or with whitespace, is refused at the email field")
    void invalidEmailsRefused() throws Exception {
        int count = accounts();
        for (String email : new String[] {"nia.example.com", "@example.com", "nia@", "@", "a@@example.com",
                "a@b@example.com", "ni a@example.com", "nia@exam ple.com", "", "   "}) {
            assertRefused(signUp("Nia", email, NIA_PASSWORD), "email");
        }
        assertRefused(signUp("Nia", null, NIA_PASSWORD), "email");
        assertThat(accounts()).isEqualTo(count);
    }

    @Test
    @DisplayName("AC-22.2 · an email of 254 characters is accepted and one of 255 is refused (implementation limit, pending a decision: not in the docs)")
    void emailLengthBoundary() throws Exception {
        String domain = "@example.com";
        String max = "a".repeat(254 - domain.length()) + domain;
        signUp("Nia", max, NIA_PASSWORD).andExpect(status().isCreated());
        assertRefused(signUp("Nia", "a" + max, NIA_PASSWORD), "email");
    }

    // --- Password (AC-22.3, EC-14) -------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-22.3 · a password of 11 characters is refused and of 12 characters is accepted")
    void passwordMinimum() throws Exception {
        assertRefused(signUp("Nia", "a@example.com", "x".repeat(11)), "password");
        signUp("Nia", "b@example.com", "x".repeat(12)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("AC-22.3 · a password of 64 characters is accepted and of 65 characters is refused")
    void passwordMaximum() throws Exception {
        signUp("Nia", "a@example.com", "x".repeat(64)).andExpect(status().isCreated());
        assertRefused(signUp("Nia", "b@example.com", "x".repeat(65)), "password");
    }

    @Test
    @DisplayName("AC-22.3 · a missing or null password is refused at the password field")
    void missingPasswordRefused() throws Exception {
        assertRefused(signUp(null, Map.of("name", "Nia", "email", "a@example.com")), "password");
        assertRefused(signUp("Nia", "a@example.com", null), "password");
    }

    @Test
    @DisplayName("EC-14 · AC-22.3 · a password of 72 UTF-8 bytes is accepted and one of 73 or more is refused, never cut short")
    void passwordByteLimit() throws Exception {
        // 2-byte characters: 36 = 72 bytes, 37 = 74 bytes (fewer than 64 characters in both).
        signUp("Nia", "a@example.com", "ä".repeat(36)).andExpect(status().isCreated());
        assertRefused(signUp("Nia", "b@example.com", "ä".repeat(37)), "password");
        // 4-byte characters: 18 = 72 bytes, 19 = 76 bytes.
        signUp("Nia", "c@example.com", "😀".repeat(18)).andExpect(status().isCreated());
        assertRefused(signUp("Nia", "d@example.com", "😀".repeat(19)), "password");
        // 64 characters of 3 bytes each: within 64 characters, over 72 bytes.
        assertRefused(signUp("Nia", "e@example.com", "中".repeat(64)), "password");
        assertThat(accountsWithEmail("b@example.com") + accountsWithEmail("d@example.com")
                + accountsWithEmail("e@example.com")).isZero();
    }

    @Test
    @DisplayName("AC-22.3 · the password is not trimmed: leading and trailing spaces are part of it")
    void passwordIsNotTrimmed() throws Exception {
        String spaced = "  spaced-pass-1  ";
        signUp("Nia", "nia@example.com", spaced).andExpect(status().isCreated());

        signInAs("nia@example.com", spaced).andExpect(status().isOk());
        signInAs("nia@example.com", spaced.trim()).andExpect(status().isUnauthorized());
        // 12 spaces are a valid password of 12 characters.
        signUp("Nia", "blank@example.com", " ".repeat(12)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("AC-22.2 · AC-22.3 · every invalid field is reported in one 422, and no account is created")
    void allInvalidFieldsReported() throws Exception {
        int count = accounts();
        signUp(" ", "nia.example.com", "short")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.details[?(@.field == 'name')]", hasSize(1)))
                .andExpect(jsonPath("$.details[?(@.field == 'email')]", hasSize(1)))
                .andExpect(jsonPath("$.details[?(@.field == 'password')]", hasSize(1)));
        assertThat(accounts()).isEqualTo(count);
    }

    // --- Control characters and sign-in input that can match no account (audit S-05, S-07; EC-14) ----------------

    @Test
    @DisplayName("AC-22.2 · audit S-05 · a name or an email with a control character is refused with 422 at that field and creates nothing")
    void controlCharactersRefused() throws Exception {
        int count = accounts();
        assertRefused(signUp("Ni\u0000a", "a@example.com", NIA_PASSWORD), "name");
        assertRefused(signUp("Ni\u0007a", "a@example.com", NIA_PASSWORD), "name");
        assertRefused(signUp("Nia", "a\u0000@example.com", NIA_PASSWORD), "email");
        assertRefused(signUp("Nia", "a\u0001@example.com", NIA_PASSWORD), "email");
        assertThat(accounts()).isEqualTo(count);
    }

    @Test
    @DisplayName("EC-14 · SP-10 · audit S-07 · a 72-byte account does not sign in with 73 bytes that share its first 72 bytes")
    void passwordOver72BytesSharingThePrefixGets401() throws Exception {
        String ok = "\u00e4".repeat(36); // 72 bytes
        signUp("Nia", "nia@example.com", ok).andExpect(status().isCreated());

        signInAs("nia@example.com", ok).andExpect(status().isOk());
        signInAs("nia@example.com", ok + "q")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"));
        signInAs("nia@example.com", "p".repeat(72) + "q".repeat(10))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"));
    }

    @Test
    @DisplayName("AC-22.5 · audit S-07 · sign-in with an email containing a control character or an invalid form gets the generic 401")
    void unmatchableSignInEmailsGetTheGeneric401() throws Exception {
        for (String email : new String[] {"ana\u0000@example.com", "ana\u0007@example.com", "not-an-email", "a@@b",
                "an a@example.com"}) {
            signInAs(email, TEST_PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("invalid_credentials"));
        }
    }

    @Test
    @DisplayName("AC-22.2 · EC-13 · an email with a character outside printable ASCII (zero-width, fullwidth, dotless i, no-break space) is refused at the email field")
    void nonAsciiEmailsRefused() throws Exception {
        int count = accounts();
        for (String email : new String[] {"nia\u200b@example.com", "\uff4eia@example.com", "n\u0131a@example.com",
                "n\u0130a@example.com", "nia@exa\u00e4mple.com", "\u00a0nia@example.com", "nia@example.com\u00a0"}) {
            assertRefused(signUp("Nia", email, NIA_PASSWORD), "email");
        }
        assertThat(accounts()).isEqualTo(count);
    }

    @Test
    @DisplayName("AC-22.2 · printable ASCII punctuation (0x21 to 0x7E) is accepted in an email")
    void printableAsciiEmailAccepted() throws Exception {
        signUp("Nia", "n.i+a_!#$%&'*-/=?^`{|}~@example.com", NIA_PASSWORD).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("AC-22.5 · EC-13 · sign-in with a non-ASCII email gets the generic 401 invalid_credentials")
    void nonAsciiSignInEmailGetsGeneric401() throws Exception {
        for (String email : new String[] {"ana\u200b@example.com", "\uff41na@example.com", "\u0430na@example.com",
                "ana@example.com\u00a0"}) {
            signInAs(email, TEST_PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("invalid_credentials"));
        }
    }

    // --- Unknown fields, malformed bodies, identity spoofing ----------------------------------------------------

    @Test
    @DisplayName("AC-INC12.1 · FR-01 · fields the client may not send (account_id, role, workspace_id) are refused with 422 and create nothing")
    void unknownFieldsRefused() throws Exception {
        int count = accounts();
        int members = jdbc.queryForObject("SELECT count(*) FROM member", Integer.class);
        for (String extra : new String[] {"account_id", "role", "workspace_id"}) {
            Map<String, Object> body = nameEmailPassword("Nia", "nia@example.com", NIA_PASSWORD);
            body.put(extra, "c0000000-0000-0000-0000-0000000000ff");
            signUp(null, body)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("validation_failed"))
                    .andExpect(jsonPath("$.details[0].field").value(extra));
        }
        assertThat(accounts()).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member", Integer.class)).isEqualTo(members);
    }

    @Test
    @DisplayName("AC-INC12.1 · FR-01 · a body that is not JSON gets 400 malformed_request and creates nothing")
    void nonJsonBodyIsMalformed() throws Exception {
        int count = accounts();
        mvc.perform(post("/auth/sign-up").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("name=Nia&email="))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed_request"));
        mvc.perform(post("/auth/sign-up").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("[1,2]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed_request"));
        assertThat(accounts()).isEqualTo(count);
    }

    // --- Duplicates (AC-22.4, EC-13, AC-INC12.3) -----------------------------------------------------------------

    @Test
    @DisplayName("AC-22.4 · EC-13 · BR-24 · an email in another case with a trailing space gets 409 email_taken and Ana's account is unchanged")
    void duplicateEmailRefused() throws Exception {
        String before = jdbc.queryForObject(
                "SELECT account_id || '|' || name || '|' || email || '|' || password_hash FROM account "
                        + "WHERE email = 'ana@example.com'", String.class);
        int count = accounts();

        signUp("Other Ana", "Ana@Example.com ", NIA_PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_taken"))
                .andExpect(jsonPath("$.details[0].field").value("email"))
                .andExpect(jsonPath("$.details", hasSize(1)));

        assertThat(accounts()).isEqualTo(count);
        assertThat(accountsWithEmail("ana@example.com")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT account_id || '|' || name || '|' || email || '|' || password_hash FROM account "
                        + "WHERE email = 'ana@example.com'", String.class)).isEqualTo(before);
        // The refused request did not start a session or change Ana's password.
        signInAs("ana@example.com", TEST_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("EC-13 · BR-24 · an email equal to an account created by sign-up, in another case or with spaces, is refused")
    void duplicateOfNewAccountRefused() throws Exception {
        signUp("Nia", "nia@example.com", NIA_PASSWORD).andExpect(status().isCreated());

        for (String again : new String[] {"nia@example.com", "NIA@EXAMPLE.COM", "  nia@example.com", "Nia@example.com\t"}) {
            signUp("Nia Two", again, "another-pw-12345")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("email_taken"))
                    .andExpect(jsonPath("$.details[0].field").value("email"));
        }
        assertThat(accountsWithEmail("nia@example.com")).isEqualTo(1);
        // The first account keeps its password.
        signInAs("nia@example.com", NIA_PASSWORD).andExpect(status().isOk());
        signInAs("nia@example.com", "another-pw-12345").andExpect(status().isUnauthorized());
    }

    // --- Sign-in matching (AC-22.5, AC-INC12.4) ------------------------------------------------------------------

    @Test
    @DisplayName("AC-22.5 · AC-INC12.4 · sign-in matches the email ignoring case and surrounding spaces")
    void signInIgnoresCaseAndSpaces() throws Exception {
        signUp("Nia", "nia@example.com", NIA_PASSWORD).andExpect(status().isCreated());

        signInAs(" NIA@Example.com ", NIA_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("nia@example.com"));
        signInAs("ANA@EXAMPLE.COM", TEST_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC-22.5 · AC-INC12.4 · a wrong password and an unknown email still get the same generic 401")
    void signInFailuresStayGeneric() throws Exception {
        signUp("Nia", "nia@example.com", NIA_PASSWORD).andExpect(status().isCreated());

        String wrong = signInAs(" NIA@Example.com ", "wrong-password-1").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials")).andReturn().getResponse()
                .getContentAsString();
        String unknown = signInAs("nobody@example.com", "wrong-password-1").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials")).andReturn().getResponse()
                .getContentAsString();
        assertThat(wrong).isEqualTo(unknown);
    }

    // --- Already signed in (AC-22.7, EC-15, AC-INC12.6) ----------------------------------------------------------

    @Test
    @DisplayName("AC-22.7 · EC-15 · AC-INC12.6 · a signed-in caller gets 409 conflict, keeps the session and no account is created")
    void signedInCallerIsRefused() throws Exception {
        MockHttpSession ana = signIn("ana@example.com");
        int count = accounts();

        signUp(ana, nameEmailPassword("Nia", "nia@example.com", NIA_PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));

        assertThat(accounts()).isEqualTo(count);
        assertThat(accountsWithEmail("nia@example.com")).isZero();
        assertThat(ana.isInvalid()).isFalse();
        mvc.perform(get("/auth/me").session(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@example.com"));
    }

    // --- A new account sees nothing of other workspaces (NFR-01, AC-22.8) ----------------------------------------

    @Test
    @DisplayName("AC-22.8 · NFR-01 · a new account has an empty project list")
    void newAccountSeesNoProjects() throws Exception {
        MockHttpSession nia = newSession();

        mvc.perform(get("/projects").session(nia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("AC-22.8 · NFR-01 · a new account gets 404 for projects and tasks of other workspaces and changes nothing")
    void newAccountCannotReachDemoData() throws Exception {
        MockHttpSession nia = newSession();
        int projects = jdbc.queryForObject("SELECT count(*) FROM project", Integer.class);
        int tasks = jdbc.queryForObject("SELECT count(*) FROM task", Integer.class);
        String task = jdbc.queryForObject("SELECT title || '|' || status FROM task WHERE task_id = '" + T_IMAGES + "'",
                String.class);

        mvc.perform(get("/projects/" + WEBSITE).session(nia)).andExpect(status().isNotFound());
        mvc.perform(patch("/projects/" + WEBSITE).session(nia).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Hijacked\"}")).andExpect(status().isNotFound());
        mvc.perform(delete("/projects/" + WEBSITE).session(nia).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/projects/" + WEBSITE + "/tasks").session(nia)).andExpect(status().isNotFound());
        mvc.perform(get("/projects/" + WEBSITE + "/tasks/" + T_IMAGES).session(nia)).andExpect(status().isNotFound());
        mvc.perform(post("/projects/" + WEBSITE + "/tasks").session(nia).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Placeholder task\",\"owner_member_id\":\"" + ACME_ANA + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/projects/" + WEBSITE + "/tasks/" + T_IMAGES).session(nia).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"Done\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/projects/" + WEBSITE + "/tasks/" + T_IMAGES).session(nia).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/projects/" + LAB).session(nia)).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM project", Integer.class)).isEqualTo(projects);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM task", Integer.class)).isEqualTo(tasks);
        assertThat(jdbc.queryForObject("SELECT title || '|' || status FROM task WHERE task_id = '" + T_IMAGES + "'",
                String.class)).isEqualTo(task);
    }

    @Test
    @DisplayName("AC-22.8 · FR-01 · a new account cannot create a project: it is a member of no workspace")
    void newAccountCannotCreateAProject() throws Exception {
        MockHttpSession nia = newSession();
        int projects = jdbc.queryForObject("SELECT count(*) FROM project", Integer.class);

        int refused = mvc.perform(post("/projects").session(nia).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workspace_id\":\"b0000000-0000-0000-0000-000000000001\",\"name\":\"Placeholder\","
                                + "\"owner_member_id\":\"" + ACME_ANA + "\"}"))
                .andReturn().getResponse().getStatus();

        // Which refusal applies (403, 404 or 422) is not fixed for an outsider; it must not be a success.
        assertThat(refused).isGreaterThanOrEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project", Integer.class)).isEqualTo(projects);
    }

    // --- Logs (AC-INC12.7, SP-10) --------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-INC12.7 · SP-10 · the password and its hash never appear in the logs, for success and for refusals")
    void passwordNeverLogged(CapturedOutput output) throws Exception {
        String secret = "log-canary-secret-pw-77";
        signUp("Nia", "nia@example.com", secret).andExpect(status().isCreated());
        signUp("Nia", "nia@example.com", secret).andExpect(status().isConflict());
        signUp("Nia", "bad-email", secret).andExpect(status().isUnprocessableEntity());
        signUp("Nia", "c@example.com", secret + "x".repeat(70)).andExpect(status().isUnprocessableEntity());
        signInAs("nia@example.com", secret).andExpect(status().isOk());
        signInAs("nia@example.com", secret + "-wrong").andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/sign-up").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + secret + "\", broken")).andExpect(status().isBadRequest());
        String hash = jdbc.queryForObject("SELECT password_hash FROM account WHERE email = 'nia@example.com'",
                String.class);

        assertThat(output.getAll()).doesNotContain(secret).doesNotContain(hash);
    }

    // --- helpers -------------------------------------------------------------------------------------------------

    private MockHttpSession newSession() throws Exception {
        return (MockHttpSession) signUp("Nia Newcomer", "nia@example.com", NIA_PASSWORD)
                .andExpect(status().isCreated()).andReturn().getRequest().getSession(false);
    }

    private static Map<String, Object> nameEmailPassword(String name, String email, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("email", email);
        body.put("password", password);
        return body;
    }
}
