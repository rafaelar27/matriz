package com.matriz.accesscontrol;

import java.io.IOException;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matriz.shared.ApiError;

/**
 * Authentication for every request (FR-01, SP-01; TD-03 decided 2026-11-12): a server-side session identified by an
 * HttpOnly, SameSite=Lax cookie, with CSRF protection for every state-changing request. Only sign-up (INC-12),
 * sign-in, the CSRF endpoint, the static user interface files and its configuration (/config.js) are open; everything
 * else requires a valid session. Sign-up, sign-in and sign-out are handled by
 * {@code com.matriz.accounts.AuthController}.
 */
@Configuration
public class SecurityConfig {

    public static final String SESSION_COOKIE = "MATRIZ_SESSION";

    /** Only for the web application; the seed command and non-web tests run without HTTP security. */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain securityFilterChain(HttpSecurity http, CookieCsrfTokenRepository csrfTokens,
                                            ObjectMapper json) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/auth/sign-in", "/auth/sign-up").permitAll()
                .requestMatchers(HttpMethod.GET, "/auth/csrf").permitAll()
                .requestMatchers("/error").permitAll()
                // The static user interface (CMP-11) holds no data; every data request still needs a session.
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/css/**", "/js/**").permitAll()
                // The interface's configuration (backend base URL, no secrets), needed before sign-in (approved 2026-11-12).
                .requestMatchers(HttpMethod.GET, "/config.js").permitAll()
                .anyRequest().authenticated())
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokens)
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
            .securityContext(ctx -> ctx.securityContextRepository(securityContextRepository()))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) -> unauthenticated(request, response, json))
                .accessDeniedHandler((request, response, e) -> {
                    ApiError body = e instanceof CsrfException
                            ? new ApiError("csrf_invalid",
                                    "The request has no valid security token. Reload the page and try again.")
                            : new ApiError("forbidden", "You do not have permission to do this.");
                    write(response, HttpServletResponse.SC_FORBIDDEN, body, json);
                }));
        return http.build();
    }

    /** 401 for every request without a valid session. An expired or ended session gets its own code. */
    private static void unauthenticated(HttpServletRequest request, HttpServletResponse response, ObjectMapper json)
            throws IOException {
        boolean sessionEnded = request.getRequestedSessionId() != null && !request.isRequestedSessionIdValid();
        ApiError body = sessionEnded
                ? new ApiError("session_expired", "Your session has ended. Sign in again.")
                : new ApiError("not_authenticated", "Sign in to continue.");
        write(response, HttpServletResponse.SC_UNAUTHORIZED, body, json);
    }

    private static void write(HttpServletResponse response, int status, ApiError body, ObjectMapper json)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), body);
    }

    /** CSRF token in a cookie readable by the front end (XSRF-TOKEN), sent back in the X-XSRF-TOKEN header. */
    @Bean
    CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        return repository;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService accounts, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(accounts);
        provider.setPasswordEncoder(encoder);
        // Unknown email and wrong password give the same result (no hint which field was wrong).
        provider.setHideUserNotFoundExceptions(true);
        return new ProviderManager(provider);
    }

    /**
     * Spring Security's documented handler for JavaScript front ends: accepts the raw token from the header and
     * makes sure the token cookie is always written.
     */
    static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

        private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
        private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> token) {
            xor.handle(request, response, token);
            token.get();
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken token) {
            String header = request.getHeader(token.getHeaderName());
            return (StringUtils.hasText(header) ? plain : xor).resolveCsrfTokenValue(request, token);
        }
    }
}
