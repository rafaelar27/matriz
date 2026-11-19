package com.matriz.accounts;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.matriz.accesscontrol.SecurityConfig;
import com.matriz.shared.ApiError;
import com.matriz.shared.ApiException;

/**
 * Sign-up, sign-in, current profile and sign-out (FR-01, SP-01, CMP-01). Sign-up and failed sign-ins are limited
 * per client address (NFR-11, SP-08).
 *
 * <p>Session lifecycle: created by a successful sign-in or sign-up (always a new session id, so an earlier session
 * cannot be reused); kept on the server, in memory; expires after the idle timeout ({@code MATRIZ_SESSION_TIMEOUT}, default
 * 8h); ended by sign-out or by restarting the application. Any request with an ended session gets 401.
 */
@RestController
@RequestMapping("/auth")
class AuthController {

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED,
                new ApiError("invalid_credentials", "The email or password is incorrect."));
    }

    private final AuthenticationManager authentication;
    private final SecurityContextRepository contexts;
    private final CookieCsrfTokenRepository csrfTokens;
    private final MembershipProvider members;
    private final SignUpService signUps;
    private final AttemptLimiter limiter;
    private final int signUpMax;
    private final Duration signUpWindow;
    private final int signInMaxPerIpAndEmail;
    private final int signInMaxPerIp;
    private final Duration signInWindow;

    AuthController(AuthenticationManager authentication, SecurityContextRepository contexts,
                   CookieCsrfTokenRepository csrfTokens, MembershipProvider members, SignUpService signUps,
                   AttemptLimiter limiter,
                   @Value("${matriz.rate-limit.sign-up.max}") int signUpMax,
                   @Value("${matriz.rate-limit.sign-up.window}") Duration signUpWindow,
                   @Value("${matriz.rate-limit.sign-in.max-per-ip-and-email}") int signInMaxPerIpAndEmail,
                   @Value("${matriz.rate-limit.sign-in.max-per-ip}") int signInMaxPerIp,
                   @Value("${matriz.rate-limit.sign-in.window}") Duration signInWindow) {
        this.authentication = authentication;
        this.contexts = contexts;
        this.csrfTokens = csrfTokens;
        this.members = members;
        this.signUps = signUps;
        this.limiter = limiter;
        this.signUpMax = signUpMax;
        this.signUpWindow = signUpWindow;
        this.signInMaxPerIpAndEmail = signInMaxPerIpAndEmail;
        this.signInMaxPerIp = signInMaxPerIp;
        this.signInWindow = signInWindow;
    }

    /** Sets the XSRF-TOKEN cookie, so a client can send its first state-changing request (sign-in). */
    @GetMapping("/csrf")
    ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    /**
     * Creates an account and signs it in (FR-01, US-22). Open to anyone who is not signed in. The client sends only
     * name, email and password; the id is chosen by the server and the account has no membership and no role.
     */
    @PostMapping("/sign-up")
    @ResponseStatus(HttpStatus.CREATED)
    Profile signUp(@RequestBody SignUpRequest body, HttpServletRequest request, HttpServletResponse response) {
        // Every request counts, including refused ones (decision log 2026-11-18).
        long wait = limiter.acquire("sign-up:" + request.getRemoteAddr(), signUpMax, signUpWindow);
        if (wait > 0) {
            throw ApiException.tooManyRequests(wait);
        }
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof SignedInAccount) {
            throw new ApiException(HttpStatus.CONFLICT, new ApiError("conflict",
                    "You are already signed in. Sign out to create another account."));
        }
        SignedInAccount principal = new SignedInAccount(signUps.signUp(body.name(), body.email(), body.password()));
        Authentication result = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                principal.getAuthorities());
        startSession(result, request, response);
        principal.eraseCredentials();
        return profileOf(principal);
    }

    @PostMapping("/sign-in")
    Profile signIn(@RequestBody SignInRequest body, HttpServletRequest request, HttpServletResponse response) {
        String address = request.getRemoteAddr();
        String email = SignUpService.normalizeEmail(body.email());
        String perIpAndEmail = "sign-in:" + address + "|"
                + (email.length() > SignUpService.EMAIL_MAX ? email.substring(0, SignUpService.EMAIL_MAX) : email);
        String perIp = "sign-in:" + address;
        // The attempt is counted before the password is checked, so parallel guesses cannot all pass the check; it is
        // given back when the attempt is not a credentials failure. Over a limit, even a correct password is refused
        // until the window ends (decision log 2026-11-18).
        long wait = limiter.acquire(perIpAndEmail, signInMaxPerIpAndEmail, signInWindow,
                AttemptLimiter.Tier.BEST_EFFORT);
        if (wait == 0) {
            wait = limiter.acquire(perIp, signInMaxPerIp, signInWindow);
            if (wait > 0) {
                limiter.release(perIpAndEmail, AttemptLimiter.Tier.BEST_EFFORT);
            }
        }
        if (wait > 0) {
            throw ApiException.tooManyRequests(wait);
        }
        boolean credentialsFailure = false;
        try {
            if (body.email() == null || body.email().isBlank() || body.password() == null
                    || body.password().isEmpty() || !SignUpService.canMatchAnAccount(body.email(), body.password())) {
                throw invalidCredentials();
            }
            Authentication result;
            try {
                result = authentication.authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated(body.email().trim(), body.password()));
            } catch (InternalAuthenticationServiceException e) {
                // The lookup itself failed (for example the database is down): not a credentials problem.
                throw e;
            } catch (AuthenticationException e) {
                throw invalidCredentials();
            }
            startSession(result, request, response);
            return profileOf((SignedInAccount) result.getPrincipal());
        } catch (ApiException e) {
            credentialsFailure = "invalid_credentials".equals(e.error().code());
            throw e;
        } finally {
            if (!credentialsFailure) {
                limiter.release(perIpAndEmail, AttemptLimiter.Tier.BEST_EFFORT);
                limiter.release(perIp);
            }
        }
    }

    /** Starts a new session and a new CSRF token for a signed-in account (also prevents session fixation). */
    private void startSession(Authentication result, HttpServletRequest request, HttpServletResponse response) {
        HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        request.getSession(true);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(result);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);

        csrfTokens.saveToken(csrfTokens.generateToken(request), request, response);
    }

    @GetMapping("/me")
    Profile me(Authentication auth) {
        return profileOf((SignedInAccount) auth.getPrincipal());
    }

    @PostMapping("/sign-out")
    ResponseEntity<Void> signOut(Authentication auth, HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request, response, auth);
        new CookieClearingLogoutHandler(SecurityConfig.SESSION_COOKIE, "XSRF-TOKEN").logout(request, response, auth);
        return ResponseEntity.noContent().build();
    }

    private Profile profileOf(SignedInAccount account) {
        return new Profile(account.accountId(), account.name(), account.email(),
                members.activeMembershipsOf(account.accountId()));
    }

    /** Sign-up body: only these three fields exist; the password never appears in logs or in {@code toString}. */
    record SignUpRequest(String name, String email, String password) {
        @Override
        public String toString() {
            return "SignUpRequest[email=" + email + "]";
        }
    }

    record SignInRequest(String email, String password) {
        @Override
        public String toString() {
            return "SignInRequest[email=" + email + "]";
        }
    }

    /** The signed-in person. Never contains the password or its hash. */
    record Profile(UUID accountId, String name, String email, List<MembershipProvider.Membership> memberships) {
    }
}
