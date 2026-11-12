package com.matriz.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Sign-in UI rework (approved 2026-11-12): the base URL served in /config.js (CMP-11) is empty or a path on the same
 * site, because the session cookie and CSRF token are same-site only (TD-03). Any other value stops startup. Plain
 * unit test: no Spring context, no database. The strict path pattern follows sign-in review finding 1 / security
 * audit finding 1 (approved 2026-11-12).
 */
class ClientConfigControllerTest {

    private static final Pattern SCRIPT =
            Pattern.compile("^window\\.MATRIZ_CONFIG = Object\\.freeze\\(\\{ apiBaseUrl: (\".*\") \\}\\);\\n$",
                    Pattern.DOTALL);

    private final ObjectMapper json = new ObjectMapper();

    private String scriptFor(String value) throws Exception {
        return new ClientConfigController(value, json).config().getBody();
    }

    @ParameterizedTest(name = "TD-03 · accepts \"{0}\"")
    @ValueSource(strings = {"", "/api", "/api/", "/matriz/api"})
    @DisplayName("TD-03 · accepts an empty value or a same-site path")
    void acceptsEmptyOrSameSitePath(String value) throws Exception {
        String script = scriptFor(value);

        Matcher m = SCRIPT.matcher(script);
        assertThat(m.matches()).as("script shape").isTrue();
        assertThat(json.readValue(m.group(1), String.class)).isEqualTo(value);
    }

    @ParameterizedTest(name = "TD-03 · rejects \"{0}\"")
    @ValueSource(strings = {"http://example.test", "//evil.test", "api", "\\api",
            "/\t/evil.test", "/\n/evil.test", "/\r/evil.test", "/api?x", "/api#x", "/a b"})
    @DisplayName("TD-03 · rejects any other base URL")
    void rejectsAnyOtherBaseUrl(String value) {
        assertThatThrownBy(() -> new ClientConfigController(value, json))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("TD-03 · a quote or closing script tag in the value is rejected")
    void quoteOrScriptTagIsRejected() {
        assertThatThrownBy(() -> new ClientConfigController("/a\"</script>", json))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("TD-03 · an accepted value is JSON-encoded into exactly one statement")
    void acceptedValueIsOneJsonEncodedStatement() throws Exception {
        String script = scriptFor("/api");

        Matcher m = SCRIPT.matcher(script);
        assertThat(m.matches()).as("single statement of the expected shape").isTrue();
        assertThat(json.readValue(m.group(1), String.class)).isEqualTo("/api");
        assertThat(script.chars().filter(c -> c == ';').count()).as("statement terminators").isEqualTo(1);
    }
}
