package com.matriz.storage;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Derives MATRIZ_DB_URL, MATRIZ_DB_USER and MATRIZ_DB_PASSWORD from the Neon variable DATABASE_URL_UNPOOLED
 * (written by "neon deploy" / "neon env pull" into .env.local), so the application can use a Neon branch without
 * copying its credentials by hand.
 *
 * <p>Only the direct (unpooled) endpoint is used, because Flyway applies migrations at startup (docs/deployment.md).
 * Explicit MATRIZ_DB_* values always win: this only fills in what is missing. Runs after the config-data
 * processor, so variables imported from .env and .env.local are already visible.
 */
public class NeonDatabaseEnvironment implements EnvironmentPostProcessor, Ordered {

    static final String NEON_VARIABLE = "DATABASE_URL_UNPOOLED";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String neonUrl = environment.getProperty(NEON_VARIABLE);
        if (neonUrl == null || neonUrl.isBlank() || environment.containsProperty("MATRIZ_DB_URL")) {
            return;
        }
        environment.getPropertySources().addLast(new MapPropertySource("neonDatabase", fromNeonUrl(neonUrl)));
    }

    /** Converts "postgresql://user:password@host[:port]/db?params" into the three MATRIZ_DB_* values. */
    static Map<String, Object> fromNeonUrl(String neonUrl) {
        // .env.local writes values in double quotes; a .properties import keeps them, so strip them here.
        String value = neonUrl.strip();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (java.net.URISyntaxException e) {
            // Never include the value or the cause: both contain the password (AGENTS.md C2).
            throw new IllegalStateException(NEON_VARIABLE + " is not a valid postgresql:// URL");
        }
        String userInfo = uri.getRawUserInfo();
        if (userInfo == null || !userInfo.contains(":")) {
            throw new IllegalStateException(NEON_VARIABLE + " has no user and password");
        }
        int colon = userInfo.indexOf(':');
        String user = decodeUriComponent(userInfo.substring(0, colon));
        String password = decodeUriComponent(userInfo.substring(colon + 1));
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + port + uri.getRawPath() + jdbcQuery(uri.getRawQuery());
        return Map.of("MATRIZ_DB_URL", jdbcUrl, "MATRIZ_DB_USER", user, "MATRIZ_DB_PASSWORD", password);
    }

    /**
     * Decodes the percent-escapes of a URI component. URLDecoder follows HTML form encoding, where "+" means a space;
     * in a URI's user info "+" is a literal plus, so it is escaped first to survive decoding.
     */
    private static String decodeUriComponent(String raw) {
        return URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    /**
     * Turns the libpq query string of a Neon URL (for example "sslmode=require&channel_binding=require") into the
     * query string of the JDBC URL, including the leading "?", or "" for none.
     */
    static String jdbcQuery(String libpqQuery) {
        // Allow-list: libpq name -> pgjdbc name. Anything else is dropped rather than guessed at.
        Map<String, String> known = Map.of("sslmode", "sslmode", "channel_binding", "channelBinding");
        Map<String, String> params = new java.util.LinkedHashMap<>();
        for (String pair : libpqQuery == null ? new String[0] : libpqQuery.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && known.containsKey(kv[0])) {
                params.put(known.get(kv[0]), kv[1]);
            }
        }
        params.putIfAbsent("sslmode", "require"); // Neon only accepts TLS; never fall back to plain text.
        return "?" + String.join("&", params.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList());
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
