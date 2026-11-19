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
 * An explicit MATRIZ_DB_URL always wins and switches this off; without it, URL, user and password all come from
 * Neon, even if MATRIZ_DB_USER or MATRIZ_DB_PASSWORD are set elsewhere. Runs after the config-data
 * processor, so variables imported from .env and .env.local are already visible.
 */
public class NeonDatabaseEnvironment implements EnvironmentPostProcessor, Ordered {

    static final String NEON_VARIABLE = "DATABASE_URL_UNPOOLED";
    static final String ENABLED_PROPERTY = "matriz.neon.enabled";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Test runs set matriz.neon.enabled=false (pom.xml, Surefire), so tests never fall through to a Neon branch:
        // without MATRIZ_DB_URL they fail at startup instead of running Flyway against Neon.
        if (!environment.getProperty(ENABLED_PROPERTY, Boolean.class, true)) {
            return;
        }
        String neonUrl = environment.getProperty(NEON_VARIABLE);
        if (neonUrl == null || neonUrl.isBlank() || environment.containsProperty("MATRIZ_DB_URL")) {
            return;
        }
        // addFirst: URL, user and password must come from the same place. Without it, a MATRIZ_DB_USER/PASSWORD left
        // in .env would outrank the derived ones and be sent to the Neon host. Only reached when no MATRIZ_DB_URL
        // is set anywhere, so explicit settings still switch this off as a whole.
        environment.getPropertySources().addFirst(new MapPropertySource("neonDatabase", fromNeonUrl(neonUrl)));
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
     * query string of the JDBC URL, including the leading "?".
     *
     * <p>The server certificate and host name are always verified: sslmode is always verify-full, whatever the URL
     * says, and the JVM's trust store is used (DefaultJavaSSLFactory), so no root certificate file is needed.
     * pgJDBC's "require" accepts any certificate and "verify-ca" does not check the host name, so neither is kept.
     * channel_binding is kept as channelBinding; anything else, including sslmode, is dropped.
     */
    static String jdbcQuery(String libpqQuery) {
        String channelBinding = null;
        for (String pair : libpqQuery == null ? new String[0] : libpqQuery.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals("channel_binding")) {
                channelBinding = kv[1];
            }
        }
        StringBuilder query = new StringBuilder("?sslmode=verify-full")
                .append("&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory");
        if (channelBinding != null) {
            query.append("&channelBinding=").append(channelBinding);
        }
        return query.toString();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
