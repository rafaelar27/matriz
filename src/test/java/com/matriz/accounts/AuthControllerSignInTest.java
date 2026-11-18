package com.matriz.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import com.matriz.shared.ApiException;

/**
 * Sign-in error mapping as pure logic (S-05 security review; FR-01, SP-01; openapi.yaml signIn). No Spring context and
 * no database. Expected: wrong credentials, other authentication failures and empty credentials give the generic 401
 * {@code invalid_credentials}; an internal failure of the user lookup (for example the database is down) is not
 * reported as wrong credentials and propagates, so the API answers 500.
 */
class AuthControllerSignInTest {

    private AuthenticationManager manager;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        manager = mock(AuthenticationManager.class);
        controller = new AuthController(manager, mock(SecurityContextRepository.class),
                mock(CookieCsrfTokenRepository.class), mock(MembershipProvider.class), mock(SignUpService.class),
                new AttemptLimiter(), 5, Duration.ofHours(1), 10, 50, Duration.ofMinutes(15));
    }

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private static void assertInvalidCredentials(Throwable t) {
        assertThat(t).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(e.error().code()).isEqualTo("invalid_credentials");
        });
    }

    @Test
    @DisplayName("S-05 · FR-01, SP-01 · an internal failure of the user lookup is not turned into invalid_credentials")
    void internalAuthenticationServiceExceptionPropagates() {
        InternalAuthenticationServiceException failure =
                new InternalAuthenticationServiceException("database unavailable");
        when(manager.authenticate(any(Authentication.class))).thenThrow(failure);

        assertThatThrownBy(() -> controller.signIn(
                new AuthController.SignInRequest("ana@example.test", "any-password"), request, response))
                .isInstanceOf(InternalAuthenticationServiceException.class)
                .isNotInstanceOf(ApiException.class)
                .isSameAs(failure);
    }

    @Test
    @DisplayName("S-05 · FR-01, SP-01 · wrong credentials give 401 invalid_credentials")
    void badCredentialsGiveGeneric401() {
        when(manager.authenticate(any(Authentication.class))).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> controller.signIn(
                new AuthController.SignInRequest("ana@example.test", "wrong-password"), request, response))
                .satisfies(AuthControllerSignInTest::assertInvalidCredentials);
    }

    @Test
    @DisplayName("S-05 · FR-01, SP-01 · blank email or empty password give 401 invalid_credentials without calling the manager")
    void emptyCredentialsGiveGeneric401WithoutCallingManager() {
        assertThatThrownBy(() -> controller.signIn(new AuthController.SignInRequest("   ", ""), request, response))
                .satisfies(AuthControllerSignInTest::assertInvalidCredentials);
        assertThatThrownBy(() -> controller.signIn(new AuthController.SignInRequest("ana@example.test", ""), request, response))
                .satisfies(AuthControllerSignInTest::assertInvalidCredentials);
        assertThatThrownBy(() -> controller.signIn(new AuthController.SignInRequest("   ", "pw"), request, response))
                .satisfies(AuthControllerSignInTest::assertInvalidCredentials);

        verifyNoInteractions(manager);
    }
}
