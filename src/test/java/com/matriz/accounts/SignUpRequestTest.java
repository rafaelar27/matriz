package com.matriz.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Request bodies never print the password (INC-12; AC-INC12.7; SP-10; US-22, FR-01). */
class SignUpRequestTest {

    @Test
    @DisplayName("AC-INC12.7 · SP-10 · SignUpRequest.toString never contains the password")
    void signUpRequestToStringHidesThePassword() {
        String text = new AuthController.SignUpRequest("Nia Newcomer", "nia@example.com",
                "fictitious-secret-pw").toString();
        assertThat(text).doesNotContain("fictitious-secret-pw").doesNotContain("password");
    }

    @Test
    @DisplayName("AC-INC12.7 · SP-10 · SignInRequest.toString never contains the password")
    void signInRequestToStringHidesThePassword() {
        String text = new AuthController.SignInRequest("nia@example.com", "fictitious-secret-pw").toString();
        assertThat(text).doesNotContain("fictitious-secret-pw");
    }
}
