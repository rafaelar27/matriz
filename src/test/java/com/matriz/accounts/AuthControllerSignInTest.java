package com.matriz.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private AttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        manager = mock(AuthenticationManager.class);
        limiter = new AttemptLimiter();
        controller = controller(10, 50);
    }

    /** A successful sign-in sets the thread-local security context; clear it so later tests on this thread are unaffected. */
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private AuthController controller(int perIpAndEmail, int perIp) {
        return new AuthController(manager, mock(SecurityContextRepository.class),
                mock(CookieCsrfTokenRepository.class), mock(MembershipProvider.class), mock(SignUpService.class),
                limiter, 5, Duration.ofHours(1), perIpAndEmail, perIp, Duration.ofMinutes(15));
    }

    private static final String EMAIL_KEY = "sign-in:127.0.0.1|ana@example.test";
    private static final String IP_KEY = "sign-in:127.0.0.1";
    private static final Duration WINDOW = Duration.ofMinutes(15);

    /** Seconds to wait if one more attempt were made with a limit of 1: 0 only if no attempt is counted. */
    private long counted(String key) {
        // The per address and email key lives in the best-effort table, the per address key in the strict one.
        return limiter.retryAfter(key, 1, WINDOW,
                key.equals(EMAIL_KEY) ? AttemptLimiter.Tier.BEST_EFFORT : AttemptLimiter.Tier.STRICT);
    }

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

    @Test
    @DisplayName("AC-22.6 · NFR-11 · audit S-02 · an internal failure of the lookup does not consume a sign-in attempt")
    void internalFailureDoesNotConsumeAnAttempt() {
        when(manager.authenticate(any(Authentication.class)))
                .thenThrow(new InternalAuthenticationServiceException("database unavailable"));

        assertThatThrownBy(() -> controller.signIn(
                new AuthController.SignInRequest("ana@example.test", "any-password"), request, response))
                .isInstanceOf(InternalAuthenticationServiceException.class);

        assertThat(counted(EMAIL_KEY)).isZero();
        assertThat(counted(IP_KEY)).isZero();
    }

    @Test
    @DisplayName("AC-22.6 · NFR-11 · a wrong password keeps its counted attempt for the address and for the address and email")
    void wrongPasswordKeepsTheAttempt() {
        when(manager.authenticate(any(Authentication.class))).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> controller.signIn(
                new AuthController.SignInRequest("Ana@Example.test ", "wrong-password"), request, response))
                .satisfies(AuthControllerSignInTest::assertInvalidCredentials);

        assertThat(counted(EMAIL_KEY)).isGreaterThanOrEqualTo(1);
        assertThat(counted(IP_KEY)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("AC-22.6 · NFR-11 · a successful sign-in does not leave a counted attempt")
    void successLeavesNoCountedAttempt() {
        SignedInAccount principal = new SignedInAccount(
                new Account(UUID.randomUUID(), "Ana Demo", "ana@example.test", "hash"));
        when(manager.authenticate(any(Authentication.class))).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));

        controller.signIn(new AuthController.SignInRequest("ana@example.test", "right-password"), request, response);

        assertThat(counted(EMAIL_KEY)).isZero();
        assertThat(counted(IP_KEY)).isZero();
    }

    @Test
    @DisplayName("AC-22.6 · NFR-11 · when the per-address limit refuses, the per address and email attempt is given back")
    void perAddressRefusalReleasesTheEmailAttempt() {
        controller = controller(10, 1);
        limiter.acquire(IP_KEY, 1, WINDOW);

        assertThatThrownBy(() -> controller.signIn(
                new AuthController.SignInRequest("ana@example.test", "any-password"), request, response))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

        assertThat(counted(EMAIL_KEY)).isZero();
        verifyNoInteractions(manager);
    }

    @Test
    @DisplayName("EC-14 · SP-10 · an email with a control character, an invalid email or a password over 72 bytes gives 401 without calling the manager, counted as a failure")
    void unmatchableCredentialsAreFailuresWithoutCallingTheManager() {
        String[] emails = {"ana\u0000@example.test", "not-an-email", "a@@b"};
        for (String email : emails) {
            assertThatThrownBy(() -> controller.signIn(
                    new AuthController.SignInRequest(email, "any-password"), request, response))
                    .satisfies(AuthControllerSignInTest::assertInvalidCredentials);
        }
        assertThatThrownBy(() -> controller.signIn(
                new AuthController.SignInRequest("ana@example.test", "\u00e4".repeat(37)), request, response))
                .satisfies(AuthControllerSignInTest::assertInvalidCredentials);

        verifyNoInteractions(manager);
        assertThat(counted(IP_KEY)).isGreaterThanOrEqualTo(1);
        assertThat(counted(EMAIL_KEY)).isGreaterThanOrEqualTo(1);
    }
}
