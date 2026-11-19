package com.matriz.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Conversion of Neon's DATABASE_URL_UNPOOLED into the MATRIZ_DB_* settings. No Spring context, no database.
 * Every host, role and password below is fictitious (AGENTS.md C2).
 */
class NeonDatabaseEnvironmentTest {

    private static final String SECRET = "fake-secret-123";
    private static final String NEON_URL = "postgresql://app_owner:" + SECRET
            + "@ep-example-123456.eu-central-1.aws.neon.tech/appdb?sslmode=require&channel_binding=require";

    private final NeonDatabaseEnvironment processor = new NeonDatabaseEnvironment();

    @Test
    @DisplayName("A Neon URL becomes a JDBC URL with user and password split out")
    void convertsNeonUrl() {
        Map<String, Object> result = NeonDatabaseEnvironment.fromNeonUrl(NEON_URL);

        assertThat(result).containsOnly(
                Map.entry("MATRIZ_DB_URL", "jdbc:postgresql://ep-example-123456.eu-central-1.aws.neon.tech/appdb"
                        + "?sslmode=require&channelBinding=require"),
                Map.entry("MATRIZ_DB_USER", "app_owner"),
                Map.entry("MATRIZ_DB_PASSWORD", SECRET));
        assertThat((String) result.get("MATRIZ_DB_URL")).doesNotContain(SECRET);
    }

    @Test
    @DisplayName("Double quotes written by .env.local are stripped")
    void stripsQuotes() {
        Map<String, Object> result = NeonDatabaseEnvironment.fromNeonUrl("  \"" + NEON_URL + "\"  ");

        assertThat(result.get("MATRIZ_DB_USER")).isEqualTo("app_owner");
        assertThat((String) result.get("MATRIZ_DB_URL")).startsWith("jdbc:postgresql://ep-example-123456");
    }

    @Test
    @DisplayName("An explicit port is kept and a percent-encoded password is decoded")
    void keepsPortAndDecodesPassword() {
        Map<String, Object> result =
                NeonDatabaseEnvironment.fromNeonUrl("postgresql://app_owner:p%40ss%3Aword@db.example.test:6543/appdb");

        assertThat(result.get("MATRIZ_DB_URL")).isEqualTo("jdbc:postgresql://db.example.test:6543/appdb?sslmode=require");
        assertThat(result.get("MATRIZ_DB_PASSWORD")).isEqualTo("p@ss:word");
    }

    @Test
    @DisplayName("A literal + in user or password stays a + (URI rules, not HTML form rules)")
    void keepsLiteralPlus() {
        Map<String, Object> result =
                NeonDatabaseEnvironment.fromNeonUrl("postgresql://app+owner:p+ss%2Bword@db.example.test/appdb");

        assertThat(result.get("MATRIZ_DB_USER")).isEqualTo("app+owner");
        assertThat(result.get("MATRIZ_DB_PASSWORD")).isEqualTo("p+ss+word");
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
    @DisplayName("libpq parameters map to the JDBC allow-list, and TLS is always required")
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
        "NULL                                        | ?sslmode=require",
        "''                                          | ?sslmode=require",
        "sslmode=require                             | ?sslmode=require",
        "sslmode=verify-full                         | ?sslmode=verify-full",
        "channel_binding=require                     | ?channelBinding=require&sslmode=require",
        "channel_binding=require&sslmode=require     | ?channelBinding=require&sslmode=require",
        "sslmode=require&options=endpoint%3Dep-x     | ?sslmode=require",
        "connect_timeout=10&application_name=x       | ?sslmode=require",
        "sslmode                                     | ?sslmode=require",
    })
    void mapsQueryParameters(String libpqQuery, String expected) {
        assertThat(NeonDatabaseEnvironment.jdbcQuery(libpqQuery)).isEqualTo(expected);
    }

    @Test
    @DisplayName("A malformed URL is refused without echoing the value (it holds the password)")
    void malformedUrlDoesNotLeakSecret() {
        String bad = "postgresql://app_owner:" + SECRET + "@bad host/appdb";

        assertThatThrownBy(() -> NeonDatabaseEnvironment.fromNeonUrl(bad))
                .isInstanceOf(IllegalStateException.class)
                .hasNoCause()
                .message().contains("DATABASE_URL_UNPOOLED").doesNotContain(SECRET).doesNotContain("app_owner");
    }

    @Test
    @DisplayName("A URL without user and password is refused without echoing the value")
    void missingCredentialsAreRefused() {
        assertThatThrownBy(() -> NeonDatabaseEnvironment.fromNeonUrl("postgresql://db.example.test/appdb"))
                .isInstanceOf(IllegalStateException.class)
                .message().contains("no user and password").doesNotContain("db.example.test");
    }

    @Test
    @DisplayName("Fills MATRIZ_DB_* from DATABASE_URL_UNPOOLED when they are not set")
    void fillsMissingSettings() {
        MockEnvironment env = new MockEnvironment().withProperty("DATABASE_URL_UNPOOLED", NEON_URL);

        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("MATRIZ_DB_USER")).isEqualTo("app_owner");
        assertThat(env.getProperty("MATRIZ_DB_PASSWORD")).isEqualTo(SECRET);
        assertThat(env.getProperty("MATRIZ_DB_URL")).startsWith("jdbc:postgresql://");
    }

    @Test
    @DisplayName("An explicit MATRIZ_DB_URL wins: nothing is derived (Render, Docker, CI)")
    void explicitSettingsWin() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("DATABASE_URL_UNPOOLED", NEON_URL)
                .withProperty("MATRIZ_DB_URL", "jdbc:postgresql://localhost:5432/matriz_test");

        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("MATRIZ_DB_URL")).isEqualTo("jdbc:postgresql://localhost:5432/matriz_test");
        assertThat(env.getProperty("MATRIZ_DB_USER")).isNull();
        assertThat(env.getPropertySources().contains("neonDatabase")).isFalse();
    }

    @Test
    @DisplayName("Without DATABASE_URL_UNPOOLED nothing is added")
    void noNeonVariableNoChange() {
        MockEnvironment env = new MockEnvironment().withProperty("DATABASE_URL_UNPOOLED", "  ");

        processor.postProcessEnvironment(env, null);

        assertThat(env.getPropertySources().contains("neonDatabase")).isFalse();
    }
}
