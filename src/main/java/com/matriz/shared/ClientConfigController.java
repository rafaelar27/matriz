package com.matriz.shared;

import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Serves /config.js, the one place where the user interface (CMP-11) learns the backend's base URL. The value comes
 * from the MATRIZ_API_BASE_URL environment setting; empty means the server that serves the page. It holds no secret.
 * Only an empty value or a path on this site (such as "/api") is accepted, because the session cookie and the CSRF
 * token are same-site only (TD-03); anything else stops the application at startup.
 */
@RestController
class ClientConfigController {

    /**
     * Empty, or path segments of unreserved characters only, such as "/api" or "/matriz/api/". This rules out other
     * hosts ("//host"), schemes, backslashes, query or fragment parts, and any space, tab or line break, which
     * browsers would drop and so turn "/\t/host" into "//host" (sign-in review finding 1, security audit finding 1;
     * approved 2026-11-12).
     */
    private static final Pattern SAME_SITE_PATH = Pattern.compile("(/[A-Za-z0-9._~-]+)*/?");

    private final String script;

    ClientConfigController(@Value("${matriz.api-base-url:}") String apiBaseUrl, ObjectMapper json)
            throws JsonProcessingException {
        String value = apiBaseUrl == null ? "" : apiBaseUrl.trim();
        if (!SAME_SITE_PATH.matcher(value).matches()) {
            throw new IllegalStateException(
                    "MATRIZ_API_BASE_URL must be empty or a path on this site starting with '/', such as /api "
                            + "(letters, digits and . _ ~ - only).");
        }
        // JSON-encoded, so the value can never break out of the string literal.
        this.script = "window.MATRIZ_CONFIG = Object.freeze({ apiBaseUrl: " + json.writeValueAsString(value) + " });\n";
    }

    @GetMapping(value = "/config.js", produces = "application/javascript")
    ResponseEntity<String> config() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.valueOf("application/javascript"))
                .body(script);
    }
}
