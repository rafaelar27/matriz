package com.matriz.accounts;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
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
import org.springframework.web.bind.annotation.RestController;

import com.matriz.accesscontrol.SecurityConfig;
import com.matriz.shared.ApiError;
import com.matriz.shared.ApiException;
import com.matriz.workspaces.MemberDirectory;

/**
 * Sign-in, current profile and sign-out (FR-01, SP-01, CMP-01).
 *
 * <p>Session lifecycle: created by a successful sign-in (always a new session id, so an earlier session cannot be
 * reused); kept on the server, in memory; expires after the idle timeout ({@code MATRIZ_SESSION_TIMEOUT}, default
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
    private final MemberDirectory members;

    AuthController(AuthenticationManager authentication, SecurityContextRepository contexts,
                   CookieCsrfTokenRepository csrfTokens, MemberDirectory members) {
        this.authentication = authentication;
        this.contexts = contexts;
        this.csrfTokens = csrfTokens;
        this.members = members;
    }

    /** Sets the XSRF-TOKEN cookie, so a client can send its first state-changing request (sign-in). */
    @GetMapping("/csrf")
    ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sign-in")
    Profile signIn(@RequestBody SignInRequest body, HttpServletRequest request, HttpServletResponse response) {
        if (body.email() == null || body.email().isBlank() || body.password() == null || body.password().isEmpty()) {
            throw invalidCredentials();
        }
        Authentication result;
        try {
            result = authentication.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.email().trim(), body.password()));
        } catch (AuthenticationException e) {
            throw invalidCredentials();
        }

        // New session for every sign-in (prevents session fixation).
        HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        request.getSession(true);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(result);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);

        // New CSRF token after sign-in.
        csrfTokens.saveToken(csrfTokens.generateToken(request), request, response);

        return profileOf((SignedInAccount) result.getPrincipal());
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

    record SignInRequest(String email, String password) {
        @Override
        public String toString() {
            return "SignInRequest[email=" + email + "]";
        }
    }

    /** The signed-in person. Never contains the password or its hash. */
    record Profile(UUID accountId, String name, String email, List<MemberDirectory.Membership> memberships) {
    }
}
